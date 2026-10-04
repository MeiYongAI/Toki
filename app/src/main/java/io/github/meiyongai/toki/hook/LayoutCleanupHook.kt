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

    /**
     * 登记控件的新语义；复用到其他栏目时解除旧规则，不影响原生选择和页面控制器。
     * @param view 原生完整控件。
     * @param element 当前语义；null 表示此控件不在净化目录中。
     * @return Unit。
     * Callers: init 中的原生控件绑定回调。
     */
    private fun bind(view: View, element: LayoutElement?) = synchronized(gate) {
        val previous = bindings.remove(view)
        if (previous != null && previous != element) gate.unbind(owners.getValue(previous), view)
        if (element != null) {
            bindings[view] = element
            gate.bind(owners.getValue(element), view)
            gate.refresh(view)
        }
    }

    /**
     * 校验原生导航和侧栏契约后安装；不删除页面、不替换点击事件、不轮询视图树。
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
        ).map { (name, element) ->
            val type = loader.loadClass(name)
            Triple(type.getMethod("onViewCreated", View::class.java), type, element)
        }.groupBy { it.first }
        val avatar = loader.loadClass("com.ss.android.ugc.aweme.feed.assem.avatar.FeedAvatarDefaultAssem")
        val follow = avatar.declaredFields.single { it.type == ViewGroup::class.java }.apply { isAccessible = true }
        val avatarCreated = avatar.getMethod("onViewCreated", View::class.java)

        HostViewVisibility.acquire(module, FEATURE)
        HookRuntime.onDispose(FEATURE) {
            owners.values.forEach(gate::detach)
            synchronized(gate) { bindings.clear() }
            tags.clear()
        }
        HookRuntime.subscribe(FEATURE) { snapshot ->
            owners.forEach { (element, owner) -> owner.clean = snapshot?.boolean(element.key) == true }
            gate.refresh()
            if (snapshot != null) HookRuntime.appliedConfiguration(FEATURE, snapshot)
        }
        module.trackHook(FEATURE, top, requiresConfiguration = false).intercept { chain ->
            val result = chain.proceed()
            val tag = topTag.invoke(chain.args[1]) as String
            val view = topView.get(result) as View
            bind(view, LayoutElement.navigation(LayoutGroup.TOP, tag))
            result
        }
        module.trackHook(FEATURE, toolbar, requiresConfiguration = false).intercept { chain ->
            val result = chain.proceed()
            bind(chain.args[0] as View, LayoutElement.navigation(LayoutGroup.TOP, chain.args[1] as String))
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
        for ((method, matches) in components) {
            module.trackHook(FEATURE, method, requiresConfiguration = false).intercept { chain ->
                val result = chain.proceed()
                matches.singleOrNull { it.second.isInstance(chain.thisObject) }?.let {
                    bind(chain.args[0] as View, it.third)
                }
                result
            }
        }
        module.trackHook(FEATURE, avatarCreated, requiresConfiguration = false).intercept { chain ->
            val result = chain.proceed()
            bind(checkNotNull(follow.get(chain.thisObject) as? View), LayoutElement.FOLLOW)
            result
        }
    }
}
