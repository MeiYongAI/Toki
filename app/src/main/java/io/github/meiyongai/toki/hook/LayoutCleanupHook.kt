package io.github.meiyongai.toki.hook

import android.view.View
import android.view.ViewGroup
import io.github.libxposed.api.XposedModule
import io.github.meiyongai.toki.model.LayoutElement
import io.github.meiyongai.toki.model.LayoutGroup
import java.lang.reflect.Modifier
import java.util.WeakHashMap

/** 在原生控件绑定入口登记隐藏所有权，视图复用和宿主重新显示均遵守当前设置。 */
object LayoutCleanupHook {
    private const val FEATURE = "LayoutCleanupHook"
    private val owners = LayoutElement.entries.associateWith { CleanViewGate.Owner(hiddenVisibility = View.GONE) }
    private val bindings = WeakHashMap<View, LayoutElement>()
    private val gate get() = HostViewVisibility.gate
    private var opacity: LayoutOpacityGate? = null

    /**
     * 登记控件的新语义；复用到其他栏目时解除旧规则，不影响原生选择和页面控制器。
     * @param view 原生完整控件。
     * @param element 当前语义；null 表示此控件不在净化目录中。
     * @param group 实际布局区域；顶部工具栏入口保留顶部透明度归属。
     * @return Unit。
     * Callers: init 中的原生控件绑定回调。
     */
    private fun bind(view: View, element: LayoutElement?, group: LayoutGroup? = element?.group) = synchronized(gate) {
        val previous = bindings.remove(view)
        checkNotNull(opacity).bind(view, group)
        if (previous != null && previous != element) gate.unbind(owners.getValue(previous), view)
        if (element != null) {
            bindings[view] = element
            gate.bind(owners.getValue(element), view)
            gate.refresh(view)
        }
    }

    /**
     * 校验原生导航、视频组件和奖励入口契约后安装；不删除页面、不替换点击事件、不轮询视图树。
     * @param module 模块实例。
     * @param loader 官方 TikTok 类加载器。
     * @return Unit；契约不满足时由注册事务撤销并报告。
     * Callers: TokiModule.installHost。
     */
    fun init(module: XposedModule, loader: ClassLoader) {
        val top = HostSymbols.resolve(loader, HostSymbol.LAYOUT_TOP_TABS).declaredMethods.single {
            it.name == HostSymbols.member(HostSymbol.LAYOUT_TOP_TABS, "bind")
        }.apply { isAccessible = true }
        check(top.parameterCount == 2 && top.parameterTypes[0] == View::class.java)
        val topTag = top.parameterTypes[1].getMethod("tag")
        check(topTag.returnType == String::class.java)
        val topView = top.returnType.declaredFields.single {
            it.type != View::class.java && it.type != top.declaringClass &&
                View::class.java.isAssignableFrom(it.type) && !Modifier.isStatic(it.modifiers)
        }.apply { isAccessible = true }
        val topStrip = loader.loadClass("com.ss.android.ugc.aweme.homepage.ui.view.tab.top.MainTabStrip")
        val topStripCreated = topStrip.getDeclaredConstructor(android.widget.FrameLayout::class.java)
        val topStripView = topStrip.declaredFields.single {
            !Modifier.isStatic(it.modifiers) && android.widget.HorizontalScrollView::class.java.isAssignableFrom(it.type)
        }.apply { isAccessible = true }
        check(topStripView.type.getMethod("setSelectedTabIndicatorHeight", Int::class.javaPrimitiveType).returnType == Void.TYPE)
        val toolbar = HostSymbols.resolve(loader, HostSymbol.LAYOUT_TOOLBAR).getDeclaredMethod(
            HostSymbols.member(HostSymbol.LAYOUT_TOOLBAR, "tag"), View::class.java, String::class.java
        ).apply { check(Modifier.isStatic(modifiers)); isAccessible = true }
        val bottomType = HostSymbols.resolve(loader, HostSymbol.LAYOUT_BOTTOM_ITEM)
        check(View::class.java.isAssignableFrom(bottomType))
        val iconData = bottomType.getMethod("getIconData").returnType
        val bottom = bottomType.getConstructor(iconData)
        val dataConstructor = iconData.superclass.getDeclaredConstructor(
            android.content.Context::class.java, String::class.java, String::class.java
        ).apply { isAccessible = true }
        val tags = java.util.Collections.synchronizedMap(WeakHashMap<Any, String>())
        val components = mapOf(
            "com.ss.android.ugc.aweme.feed.landscape.LandscapeEntranceAssem" to LayoutElement.LANDSCAPE,
            "com.ss.android.ugc.aweme.ui.feed.photos.assem.PhotoNumIndicatorAssem" to LayoutElement.PHOTO_INDICATORS,
            "com.ss.android.ugc.aweme.ui.feed.photos.assem.PhotoSlideIndicatorAssem" to LayoutElement.PHOTO_INDICATORS,
            "com.ss.android.ugc.aweme.ui.feed.photos.assem.AbsPhotosDotIndicatorAssem" to LayoutElement.PHOTO_INDICATORS,
            "com.ss.android.ugc.aweme.ui.feed.photos.assem.TopPhotosDotIndicatorAssem" to LayoutElement.PHOTO_INDICATORS,
            "com.ss.android.ugc.aweme.ui.feed.photos.assem.TabletsPhotosDotIndicatorAssem" to LayoutElement.PHOTO_INDICATORS,
            "com.ss.android.ugc.aweme.ui.feed.photos.assem.PhotoSwipeHintAssem" to LayoutElement.PHOTO_INDICATORS,
            "com.ss.android.ugc.aweme.feed.assem.quickreply.QuickDMBoxAssem" to LayoutElement.QUICK_MESSAGE,
            "com.ss.android.ugc.aweme.feed.assem.quickreply.MUFQuickDMBoxAssem" to LayoutElement.QUICK_MESSAGE,
            "com.ss.android.ugc.aweme.feed.assem.quickreply.MUFQuickDMBoxAssemV2" to LayoutElement.QUICK_MESSAGE,
            "com.ss.android.ugc.aweme.feed.assem.story.QuickDMEntranceAssem" to LayoutElement.QUICK_MESSAGE,
            "com.ss.android.ugc.aweme.feed.assem.story.QuickDMEntranceAssemV2" to LayoutElement.QUICK_MESSAGE,
            "com.ss.android.ugc.aweme.feed.assem.unreadshare.ShareUnreadVideoQuickDMAssem" to LayoutElement.QUICK_MESSAGE,
            "com.ss.android.ugc.aweme.feed.assem.sharer.VideoExposeSharerInformationAssem" to LayoutElement.QUICK_MESSAGE,
            "com.ss.android.ugc.aweme.story.feed.immersive.dm.StoryQuickDMBottomBarAssem" to LayoutElement.QUICK_MESSAGE,
            "com.ss.android.ugc.aweme.feed.assem.usercard.VideoUserCardAssem" to LayoutElement.USER_CARD,
            "com.ss.android.ugc.feed.platform.cell.interact.bottom.bar.BottomSurveyAssem" to LayoutElement.SURVEY,
            "com.ss.android.ugc.aweme.feed.assem.pushsurvey.PushSurveyAssem" to LayoutElement.SURVEY,
            "com.ss.android.ugc.aweme.feed.assem.earlyfeedback.EarlyFeedbackButtonAssem" to LayoutElement.SURVEY,
            "com.ss.android.ugc.aweme.commercialize.feed.assem.product.AdProductTileAssem" to LayoutElement.AD_CARDS,
            "com.ss.android.ugc.aweme.commercialize.feed.assem.interactivead.AdInteractiveAssem" to LayoutElement.AD_CARDS,
            "com.ss.android.ugc.aweme.commercialize.feed.assem.playfun.AdPlayFunAssem" to LayoutElement.AD_CARDS,
            "com.ss.android.ugc.aweme.feed.assem.commoditycard.CustomCommodityCardAssem" to LayoutElement.AD_CARDS,
            "com.ss.android.ugc.aweme.ad.feed.liveshopping.AdAnoleSlotBottomBannerAssem" to LayoutElement.AD_CARDS,
            "com.ss.android.ugc.feed.platform.cell.interact.bottom.banner.FeedCommonBannerAssem" to LayoutElement.BOTTOM_BANNERS,
            "com.ss.android.ugc.feed.platform.cell.interact.bottom.banner.standard.StandardInteractBottomBannerAssem" to LayoutElement.BOTTOM_BANNERS,
            "com.ss.android.ugc.feed.platform.cell.interact.bottom.bar.InteractReferralBottomBannerAssem" to LayoutElement.REWARDS,
            "com.ss.android.ugc.feed.platform.cell.interact.bottom.bar.GiftBagBottomBarAssem" to LayoutElement.REWARDS,
            "com.ss.android.ugc.feed.platform.cell.interact.bottom.bar.LiveTaskBarBottomBarAssem" to LayoutElement.REWARDS,
            "com.ss.android.ugc.aweme.feed.assem.collab.CollabInvitedButtonAssem" to LayoutElement.CREATION_BUTTONS,
            "com.ss.android.ugc.aweme.feed.assem.analyticsinspiration.AnalyticsInspirationButtonAssem" to LayoutElement.CREATION_BUTTONS,
            "com.ss.android.ugc.aweme.story.feed.immersive.component.bottombutton.addtostorybutton.assem.AddToStoryButtonFeedAssem" to LayoutElement.CREATION_BUTTONS,
            "com.ss.android.ugc.aweme.feed.assem.tikbot.TakoAssem" to LayoutElement.TAKO,
            "com.ss.android.ugc.aweme.feed.assem.tikbot.TakoAssemRoof" to LayoutElement.TAKO,
            "com.ss.android.ugc.aweme.feed.assem.incentive.IncentiveBottomButtonAssem" to LayoutElement.REWARDS,
            "com.ss.android.ugc.aweme.feed.assem.incentive.IncentiveShareButtonAssem" to LayoutElement.REWARDS,
            "com.ss.android.ugc.aweme.feed.assem.quickcomment.VideoQuickCommentAssem" to LayoutElement.QUICK_COMMENT,
            "com.ss.android.ugc.aweme.feed.assem.quickcomment.ZeroCommentQuickCommentAssem" to LayoutElement.QUICK_COMMENT,
            "com.ss.android.ugc.feed.platform.cell.interact.bottom.bar.InteractPlayListBottomBarAssem" to LayoutElement.PLAYLIST,
            "com.ss.android.ugc.feed.platform.cell.interact.bottom.bar.PlayListStandardBannerAssem" to LayoutElement.PLAYLIST,
            "com.ss.android.ugc.aweme.feed.assem.duetbutton.VideoDuetButtonAssem" to LayoutElement.CREATION_BUTTONS,
            "com.ss.android.ugc.aweme.feed.assem.stitchbutton.VideoStitchButtonAssem" to LayoutElement.CREATION_BUTTONS,
            "com.ss.android.ugc.aweme.feed.assem.pugc.VideoTemplateButtonAssem" to LayoutElement.CREATION_BUTTONS,
            "com.ss.android.ugc.aweme.feed.assem.addyours.AddYoursEntranceButtonAssem" to LayoutElement.CREATION_BUTTONS,
            "com.ss.android.ugc.aweme.feed.assem.live.LiveNoticeCTAButtonAssem" to LayoutElement.LIVE_NOTICE,
            "com.ss.android.ugc.feed.platform.cell.interact.bottom.bar.AdFeedSearchBottomBarAssem" to LayoutElement.SEARCH_LABEL,
            "com.ss.android.ugc.feed.platform.cell.interact.bottom.bar.FeedEcSearchBottomBarAssem" to LayoutElement.SEARCH_LABEL,
            "com.ss.android.ugc.feed.platform.panel.autoscroll.AutoScrollComponent" to LayoutElement.AUTO_SCROLL_STOP,
            "com.ss.android.ugc.aweme.feed.assem.avatar.FeedAvatarAssemWrap" to LayoutElement.AVATAR,
            "com.ss.android.ugc.aweme.feed.assem.digg.VideoDiggAssem" to LayoutElement.LIKE,
            "com.ss.android.ugc.aweme.feed.assem.videocomment.VideoCommentAssem" to LayoutElement.COMMENT,
            "com.ss.android.ugc.aweme.feed.favorite.VideoFavoriteAssem" to LayoutElement.FAVORITE,
            "com.ss.android.ugc.aweme.feed.assem.share.VideoShareAssem" to LayoutElement.SHARE,
            "com.ss.android.ugc.aweme.feed.assem.music.VideoMusicCoverAssem" to LayoutElement.MUSIC,
            "com.ss.android.ugc.aweme.feed.assem.videoauthorinfo.VideoAuthorInfoRelationAssem" to LayoutElement.AUTHOR,
            "com.ss.android.ugc.aweme.feed.assem.desc.VideoDescAssem" to LayoutElement.DESCRIPTION,
            "com.ss.android.ugc.aweme.feed.assem.music.VideoMusicTitleAssem" to LayoutElement.MUSIC_TITLE,
            "com.ss.android.ugc.aweme.feed.assem.videoauthorinfo.VideoInfoGenreTagContainerAssem" to LayoutElement.TAGS,
            "com.ss.android.ugc.feed.platform.cell.interact.info.horiontag.FcpHighTagAssem" to LayoutElement.TAGS,
            "com.ss.android.ugc.feed.platform.cell.interact.info.horiontag.FcpMetaTagAssem" to LayoutElement.TAGS,
            "com.ss.android.ugc.feed.platform.cell.interact.info.bottomlabel.FcpBottomTagContainerAssem" to LayoutElement.TAGS,
            "com.ss.android.ugc.feed.platform.cell.interact.bottom.bar.FeedSearchBottomBarAssem" to LayoutElement.SEARCH_LABEL
        ).toMutableMap().apply {
            // 新增变体按实际组件能力登记；主体入口仍执行严格契约验证。
            for ((name, element) in mapOf(
                "com.ss.android.ugc.aweme.feed.assem.duetbutton.VideoDuetStandardButtonAssem" to LayoutElement.CREATION_BUTTONS,
                "com.ss.android.ugc.aweme.feed.assem.quickcomment.HelpPostQuickCommentAssem" to LayoutElement.QUICK_COMMENT,
            )) {
                val variant = try {
                    loader.loadClass(name)
                } catch (missing: ClassNotFoundException) {
                    HookRuntime.event(FEATURE, "宿主未提供可选布局组件：$name")
                    null
                }
                if (variant != null) put(name, element)
            }
        }.map { (name, element) ->
            val type = loader.loadClass(name)
            Triple(type.getMethod("onViewCreated", View::class.java), type, element)
        }
        val componentTypes = components.map { it.second to it.third }
        val avatar = loader.loadClass("com.ss.android.ugc.aweme.feed.assem.avatar.FeedAvatarDefaultAssem")
        val follow = avatar.declaredFields.single { it.type == ViewGroup::class.java }.apply { isAccessible = true }
        val avatarCreated = avatar.getMethod("onViewCreated", View::class.java)
        val rewardWidget = loader.loadClass("com.bytedance.touchpoint.ui.pendant.SpecActWidget")
        val rewardBind = rewardWidget.getMethod("bind", ViewGroup::class.java)
        val rewardView = rewardWidget.getDeclaredField("specActStaticView").apply {
            check(View::class.java.isAssignableFrom(type))
            isAccessible = true
        }
        val timer = loader.loadClass("com.bytedance.touchpoint.core.pendant.base.BaseTimerPendantManager")
        val timerBind = timer.declaredMethods.single {
            it.parameterTypes.contentEquals(arrayOf(android.content.Context::class.java, ViewGroup::class.java)) &&
                it.returnType == Void.TYPE
        }.apply { isAccessible = true }
        val timerView = timer.declaredFields.single {
            View::class.java.isAssignableFrom(it.type) && !Modifier.isStatic(it.modifiers)
        }.apply { isAccessible = true }
        val feedTimers = listOf(
            "com.bytedance.touchpoint.core.pendant.feed.FeedTimerPendantManger",
            "com.bytedance.touchpoint.core.pendant.ad.AdFeedTimerPendantManager",
            "com.bytedance.touchpoint.core.pendant.videodetail.DetailTaskEventPendantManager",
            "com.bytedance.touchpoint.core.pendant.ad.ADTaskEventPendantManager",
            "com.bytedance.touchpoint.core.pendant.ad.ADNewStarTimerPendantManager"
        ).map(loader::loadClass)

        val opacityGate = LayoutOpacityGate()
        opacity = opacityGate
        HostViewVisibility.acquire(module, FEATURE)
        HookRuntime.onDispose(FEATURE) {
            opacityGate.close()
            opacity = null
            owners.values.forEach(gate::detach)
            synchronized(gate) { bindings.clear() }
            tags.clear()
        }
        HookRuntime.subscribe(FEATURE) { snapshot ->
            opacityGate.configure(LayoutGroup.entries.associateWith { group ->
                if (snapshot?.boolean(group.opacityEnabledKey) == true)
                    checkNotNull(snapshot.string(group.opacityKey, "100")).toInt() / 100f else 1f
            })
            owners.forEach { (element, owner) -> owner.clean = snapshot?.boolean(element.key) == true }
            gate.refresh()
            if (snapshot != null) HookRuntime.appliedConfiguration(FEATURE, snapshot)
        }
        module.trackHook(FEATURE, opacityGate.setter, requiresConfiguration = false).intercept { chain ->
            val requested = chain.args[0] as Float
            val effective = opacityGate.alpha(chain.thisObject as View, requested)
            if (effective == requested) chain.proceed() else chain.proceed(arrayOf(effective))
        }
        module.trackHook(FEATURE, top, requiresConfiguration = false).intercept { chain ->
            val result = chain.proceed()
            val tag = topTag.invoke(chain.args[1]) as String
            val view = topView.get(result) as View
            bind(view, LayoutElement.navigation(LayoutGroup.TOP, tag))
            result
        }
        module.trackHook(FEATURE, topStripCreated, requiresConfiguration = false).intercept { chain ->
            val result = chain.proceed()
            opacityGate.bind(topStripView.get(chain.thisObject) as View, LayoutGroup.TOP)
            result
        }
        module.trackHook(FEATURE, toolbar, requiresConfiguration = false).intercept { chain ->
            val result = chain.proceed()
            val element = LayoutElement.toolbar(chain.args[1] as String)
            bind(chain.args[0] as View, element, if (element != null) LayoutGroup.TOP else null)
            result
        }
        module.trackHook(FEATURE, dataConstructor, requiresConfiguration = false).intercept { chain ->
            val result = chain.proceed()
            tags[checkNotNull(chain.thisObject)] = chain.args[1] as String
            result
        }
        module.trackHook(FEATURE, bottom, requiresConfiguration = false).intercept { chain ->
            val result = chain.proceed()
            val tag = checkNotNull(tags[chain.args[0]]) { "底栏控件未经过原生标签初始化" }
            bind(chain.thisObject as View, LayoutElement.navigation(LayoutGroup.BOTTOM, tag))
            result
        }
        for (method in components.map { it.first }.distinct()) {
            module.trackHook(FEATURE, method, requiresConfiguration = false).intercept { chain ->
                val result = chain.proceed()
                LayoutComponentSelector.select(checkNotNull(chain.thisObject), componentTypes)?.let {
                    bind(chain.args[0] as View, it)
                }
                result
            }
        }
        module.trackHook(FEATURE, avatarCreated, requiresConfiguration = false).intercept { chain ->
            val result = chain.proceed()
            bind(checkNotNull(follow.get(chain.thisObject) as? View), LayoutElement.FOLLOW)
            result
        }
        module.trackHook(FEATURE, rewardBind, requiresConfiguration = false).intercept { chain ->
            val result = chain.proceed()
            (rewardView.get(chain.thisObject) as? View)?.let { bind(it, LayoutElement.REWARDS) }
            result
        }
        module.trackHook(FEATURE, timerBind, requiresConfiguration = false).intercept { chain ->
            val result = chain.proceed()
            if (feedTimers.any { it.isInstance(chain.thisObject) }) {
                (timerView.get(chain.thisObject) as? View)?.let { bind(it, LayoutElement.REWARDS) }
            }
            result
        }
    }
}
