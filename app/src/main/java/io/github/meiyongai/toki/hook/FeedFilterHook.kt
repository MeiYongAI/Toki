package io.github.meiyongai.toki.hook

import android.content.Context
import android.util.Log
import io.github.meiyongai.toki.model.KeywordFilterConfig
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.meiyongai.toki.provider.ConfigSnapshot
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

/** 在网络、缓存、推荐数据模型及 Adapter 数据准入处使用同一策略，不修改 ViewHolder 布局。 */
object FeedFilterHook {
    private const val TAG = "TokiFeedFilter"
    const val KEY_FEED_REMOVE_ADS = "feed_remove_ads"
    const val KEY_FEED_REMOVE_LIVE = "feed_remove_live"
    const val KEY_FEED_REMOVE_IMAGE = "feed_remove_image"
    const val KEY_FILTER_VIEWS_ENABLED = "feed_filter_views_enabled"
    const val KEY_FILTER_VIEWS_MIN = "feed_filter_views_min"
    const val KEY_FILTER_VIEWS_MAX = "feed_filter_views_max"
    const val KEY_FILTER_LIKES_ENABLED = "feed_filter_likes_enabled"
    const val KEY_FILTER_LIKES_MIN = "feed_filter_likes_min"
    const val KEY_FILTER_LIKES_MAX = "feed_filter_likes_max"
    const val KEY_FILTER_DURATION_ENABLED = "feed_filter_duration_enabled"
    const val KEY_FILTER_DURATION_MIN = "feed_filter_duration_min"
    const val KEY_FILTER_DURATION_MAX = "feed_filter_duration_max"
    const val KEY_FILTER_KEYWORDS_ENABLED = "feed_filter_keywords_enabled"
    const val KEY_FILTER_KEYWORDS_JSON = "feed_filter_keywords_json"
    const val KEY_REMOVE_OFFLINE = "feed_remove_offline"
    const val KEY_REMOVE_AI_GENERATED = "feed_remove_ai_generated"
    const val KEY_REMOVE_TOPIC_RECOMMENDATIONS = "feed_remove_topic_recommendations"
    const val KEY_REMOVE_CREATOR_RECOMMENDATIONS = "feed_remove_creator_recommendations"
    @Volatile private var cachedPolicy: Pair<ConfigSnapshot, FeedFilterPolicy>? = null

    /**
     * 解析关键词分类配置，保持管理界面的序列化协议。
     * @param jsonStr 配置 JSON。
     * @return 分类配置。
     * Callers: DashboardScreen、policy。
     */
    fun parseKeywordConfig(jsonStr: String?): KeywordFilterConfig = KeywordFilterConfig.fromJson(jsonStr)

    /**
     * 序列化分类配置。
     * @param config 分类配置。
     * @return JSON 文本。
     * Callers: DashboardScreen、serializeKeywords。
     */
    fun serializeKeywordConfig(config: KeywordFilterConfig): String = config.toJson()

    /**
     * 提取文案分类词条。
     * @param jsonStr 配置 JSON。
     * @return 文案词条。
     * Callers: 管理界面配置读取。
     */
    fun parseKeywords(jsonStr: String?): List<String> = parseKeywordConfig(jsonStr).desc

    /**
     * 序列化文案词条。
     * @param list 文案词条。
     * @return JSON 文本。
     * Callers: 管理界面配置写入。
     */
    fun serializeKeywords(list: List<String>): String = serializeKeywordConfig(KeywordFilterConfig(desc = list))

    /**
     * 解析点赞数下限，保持设置界面的输入语义。
     * @param raw 用户输入文本。
     * @return 正整数边界；未设置或无效输入为 0。
     * Callers: DashboardScreen、policy。
     */
    fun parseLikeMin(raw: String?): Long = raw?.trim()?.toLongOrNull()?.coerceAtLeast(0) ?: 0L

    /**
     * 解析点赞数上限，保持设置界面的输入语义。
     * @param raw 用户输入文本。
     * @return 正整数边界；未设置或无效输入为 0。
     * Callers: DashboardScreen、policy。
     */
    fun parseLikeMax(raw: String?): Long = raw?.trim()?.toLongOrNull()?.coerceAtLeast(0) ?: 0L

    /**
     * 解析浏览数下限，保持设置界面的输入语义。
     * @param raw 用户输入文本。
     * @return 正整数边界；未设置或无效输入为 0。
     * Callers: DashboardScreen、policy。
     */
    fun parseViewMin(raw: String?): Long = raw?.trim()?.toLongOrNull()?.coerceAtLeast(0) ?: 0L

    /**
     * 解析浏览数上限，保持设置界面的输入语义。
     * @param raw 用户输入文本。
     * @return 正整数边界；未设置或无效输入为 0。
     * Callers: DashboardScreen、policy。
     */
    fun parseViewMax(raw: String?): Long = raw?.trim()?.toLongOrNull()?.coerceAtLeast(0) ?: 0L

    /**
     * 解析时长秒数下限，保持设置界面的输入语义。
     * @param raw 用户输入文本。
     * @return 正整数边界；未设置或无效输入为 0。
     * Callers: DashboardScreen、policy。
     */
    fun parseDurationMin(raw: String?): Long = raw?.trim()?.toLongOrNull()?.coerceAtLeast(0) ?: 0L

    /**
     * 解析时长秒数上限，保持设置界面的输入语义。
     * @param raw 用户输入文本。
     * @return 正整数边界；未设置或无效输入为 0。
     * Callers: DashboardScreen、policy。
     */
    fun parseDurationMax(raw: String?): Long = raw?.trim()?.toLongOrNull()?.coerceAtLeast(0) ?: 0L

    /**
     * 确保配置中心已初始化。
     * @param context 已附加的宿主上下文。
     * @return Unit。
     * Callers: TokiModule.hookApplication。
     */
    fun refreshConfig(context: Context) = ConfigClient.init(context)

    /**
     * 从单一配置快照构造全部过滤策略，包含彼此独立的话题与创作者推荐开关。
     * 无入参；配置未变时复用解析结果。
     * @return 本批次使用的不可变策略。
     * Callers: init、hookAdapter、hookRepositories、hookSharedModelAds。
     */
    @Synchronized
    private fun policy(snapshot: ConfigSnapshot = ConfigClient.snapshot()): FeedFilterPolicy {
        cachedPolicy?.let { if (it.first === snapshot) return it.second }
        val value = FeedFilterPolicy(
            removeAds = snapshot.boolean(KEY_FEED_REMOVE_ADS),
            removeLive = snapshot.boolean(KEY_FEED_REMOVE_LIVE),
            removePhoto = snapshot.boolean(KEY_FEED_REMOVE_IMAGE),
            removeOffline = snapshot.boolean(KEY_REMOVE_OFFLINE),
            removeAiGenerated = snapshot.boolean(KEY_REMOVE_AI_GENERATED),
            removeTopicRecommendations = snapshot.boolean(KEY_REMOVE_TOPIC_RECOMMENDATIONS),
            removeCreatorRecommendations = snapshot.boolean(KEY_REMOVE_CREATOR_RECOMMENDATIONS),
            views = if (snapshot.boolean(KEY_FILTER_VIEWS_ENABLED)) FeedRange(
                parseViewMin(snapshot.string(KEY_FILTER_VIEWS_MIN)),
                parseViewMax(snapshot.string(KEY_FILTER_VIEWS_MAX))) else FeedRange(),
            likes = if (snapshot.boolean(KEY_FILTER_LIKES_ENABLED)) FeedRange(
                parseLikeMin(snapshot.string(KEY_FILTER_LIKES_MIN)),
                parseLikeMax(snapshot.string(KEY_FILTER_LIKES_MAX))) else FeedRange(),
            duration = if (snapshot.boolean(KEY_FILTER_DURATION_ENABLED)) FeedRange(
                FeedRange.milliseconds(parseDurationMin(snapshot.string(KEY_FILTER_DURATION_MIN))),
                FeedRange.milliseconds(parseDurationMax(snapshot.string(KEY_FILTER_DURATION_MAX)))) else FeedRange(),
            keywords = if (snapshot.boolean(KEY_FILTER_KEYWORDS_ENABLED))
                parseKeywordConfig(snapshot.string(KEY_FILTER_KEYWORDS_JSON)) else KeywordFilterConfig()
        )
        cachedPolicy = snapshot to value
        HookRuntime.detail("FeedFilterHook", "配置版本 ${snapshot.revision}；时长毫秒范围 " +
            "${value.duration.min}～${value.duration.max}（0 为未设置边界）；" +
            "文案/标签/作者关键词数 ${value.keywords.desc.size}/${value.keywords.tag.size}/${value.keywords.author.size}")
        Log.i(TAG, "配置 revision=${snapshot.revision}; durationMs=${value.duration}; " +
            "keywords=${value.keywords.desc.size}/${value.keywords.tag.size}/${value.keywords.author.size}")
        return value
    }

    /**
     * 逐组验证成员契约并注册网络、缓存、推荐数据模型、Adapter 和离线恢复控制。
     * @param module LSPosed 模块。
     * @param classLoader 宿主最终类加载器。
     * @return Unit；契约不满足时报告具体成员，不忽略解析异常。
     * Callers: TokiModule.onPackageReady。
     */
    fun init(module: XposedModule, classLoader: ClassLoader) {
        HookRuntime.onDispose("FeedFilterHook") { cachedPolicy = null }
        val reader = FeedModelReader(classLoader)
        val responseType = classLoader.loadClass("com.ss.android.ugc.aweme.feed.model.FeedItemList")
        val api = classLoader.loadClass("com.ss.android.ugc.aweme.feed.api.FeedApi")
        val network = api.declaredMethods.single {
            it.name == "LIZ" && it.returnType == responseType && it.parameterCount == 1
        }.apply { isAccessible = true }
        val feedType = network.parameterTypes.single().getDeclaredField("LIZ").apply {
            check(type == Int::class.javaPrimitiveType) { "Feed 请求类型字段不是 int" }
            isAccessible = true
        }
        val cold = HostSymbols.resolve(classLoader, HostSymbol.COLD_FEED).getDeclaredMethod("LJIJJ")
        val preload = HostSymbols.resolve(classLoader, HostSymbol.PRELOADED_FEED).declaredMethods.single {
            it.name == "getData" && it.parameterCount == 0 && it.returnType == responseType
        }
        val getItems = responseType.getMethod("getItems")
        val setItems = responseType.getMethod("setItems", List::class.java)
        val preloadAds = responseType.getDeclaredField("preloadAds").apply { isAccessible = true }

        val interceptResponse: (XposedInterface.Chain, FeedScope) -> Any? = { chain, scope ->
            val response = chain.proceed()
            if (response != null) {
                val rules = policy()
                if (rules.applies(scope)) {
                    val items = getItems.invoke(response) as List<*>?
                    if (items != null) setItems.invoke(response, filter(items, rules, scope, reader))
                    val ads = preloadAds.get(response) as List<*>?
                    if (ads != null) preloadAds.set(response, filter(ads, rules, scope, reader))
                }
            }
            response
        }
        module.trackHook("FeedFilterHook", network).setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH).intercept { chain ->
            interceptResponse(chain, FeedScope.fromFeedType(feedType.getInt(chain.args[0])))
        }
        for (method in listOf(cold, preload)) {
            check(method.returnType == responseType) { "缓存返回类型不匹配: $method" }
            method.isAccessible = true
            module.trackHook("FeedFilterHook", method).setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH).intercept { chain ->
                interceptResponse(chain, FeedScope.FOR_YOU)
            }
        }
        val offlineSwitch = HostSymbols.resolve(classLoader, HostSymbol.OFFLINE_RECOVERY)
            .getDeclaredMethod("LIZIZ").apply {
                check(returnType == Boolean::class.javaPrimitiveType)
                isAccessible = true
            }
        module.trackHook("FeedFilterHook", offlineSwitch).intercept { chain ->
            if (ConfigClient.getBoolean(KEY_REMOVE_OFFLINE)) false else chain.proceed()
        }
        hookRepositories(module, classLoader, reader)
        hookAdapter(module, classLoader, reader)
        Log.i(TAG, "信息流过滤已注册：网络、缓存、推荐数据模型与 Adapter 准入")
    }

    /**
     * 将输入列表复制为可写的合规列表，保留原有条目顺序。
     * @param items 宿主输入列表，可以不可修改。
     * @param rules 本批配置策略。
     * @param scope 明确的数据作用域。
     * @param reader 已解析的模型读取器。
     * @return 可写列表；空结果不会回填任何条目。
     * Callers: init 的响应拦截、hookAdapter、hookRepositories。
     */
    private fun filter(items: List<*>, rules: FeedFilterPolicy, scope: FeedScope, reader: FeedModelReader): ArrayList<Any> {
        HookRuntime.count("FeedFilterHook", "批次:$scope")
        val accepted = ArrayList<Any>(items.size)
        val rejected = linkedMapOf<String, Long>()
        for (item in items.filterNotNull()) {
            val entry = reader.read(item, scope)
            if (scope == FeedScope.FOR_YOU && !entry.photo && entry.durationMs == 0L) {
                HookRuntime.count("FeedFilterHook", "时长未提供")
            }
            val reason = rules.reason(entry, scope)
            if (reason == null) accepted.add(item)
            else rejected[reason] = (rejected[reason] ?: 0) + 1
        }
        rejected.forEach { (reason, count) -> HookRuntime.count("FeedFilterHook", "拒绝:$reason", count) }
        HookRuntime.count("FeedFilterHook", "通过:$scope", accepted.size.toLong())
        Log.d(TAG, "scope=$scope in=${items.size} out=${accepted.size} rejected=$rejected")
        return accepted
    }

    /**
     * 在列表提交、单条插入、替换和区间插入之前阻止目标内容进入 Adapter。
     * @param module LSPosed 模块。
     * @param loader 宿主最终类加载器。
     * @param reader 已解析的模型读取器。
     * @return Unit；不修改 ViewHolder 的布局、可见性或绑定结果。
     * Callers: init。
     */
    private fun hookAdapter(module: XposedModule, loader: ClassLoader, reader: FeedModelReader) {
        val adapter = HostSymbols.resolve(loader, HostSymbol.FEED_ADAPTER)
        val recommendAdapter = HostSymbols.resolve(loader, HostSymbol.RECOMMEND_ADAPTER)
        val paramsType = loader.loadClass("com.ss.android.ugc.aweme.feed.model.BaseFeedPageParams")
        val pageParams = adapter.declaredFields.single { it.type == paramsType }.apply { isAccessible = true }
        val eventType = paramsType.getMethod("getEventType")
        val intType = Int::class.javaPrimitiveType!!
        val awemeType = reader.awemeType
        val setData = adapter.getDeclaredMethod("setData", List::class.java)
        val getData = adapter.getDeclaredMethod("LJJJLL").apply {
            check(returnType == List::class.java)
            isAccessible = true
        }
        val activeAdapters = java.util.WeakHashMap<Any, Boolean>()
        HookRuntime.onDispose("FeedFilterHook") { synchronized(activeAdapters) { activeAdapters.clear() } }
        val insert = adapter.getDeclaredMethod(HostSymbols.member(HostSymbol.FEED_ADAPTER, "insert"), intType, awemeType)
        val replace = adapter.getDeclaredMethod(HostSymbols.member(HostSymbol.FEED_ADAPTER, "replace"), intType, awemeType)
        val removeAndInsert = adapter.getDeclaredMethod(HostSymbols.member(HostSymbol.FEED_ADAPTER, "removeInsert"), intType, awemeType)
        val batch = adapter.getDeclaredMethod(HostSymbols.member(HostSymbol.FEED_ADAPTER, "batch"), intType, intType, List::class.java)
        val recommendBatch = recommendAdapter.getDeclaredMethod(HostSymbols.member(HostSymbol.RECOMMEND_ADAPTER, "batch"), intType, intType, List::class.java)
        val remove = adapter.getDeclaredMethod("LJJJJLL", intType, awemeType).apply { isAccessible = true }
        val notify = adapter.getMethod("notifyDataSetChanged")
        val count = adapter.getMethod("getCount")
        for (constructor in adapter.declaredConstructors) {
            module.trackHook("FeedFilterHook", constructor).intercept { chain ->
                val result = chain.proceed()
                synchronized(activeAdapters) { activeAdapters[checkNotNull(chain.thisObject)] = true }
                result
            }
        }
        for (method in listOf(setData, insert, replace, removeAndInsert, batch, recommendBatch)) {
            check(method.returnType == Void.TYPE) { "Adapter 准入方法必须返回 void: $method" }
            method.isAccessible = true
            module.trackHook("FeedFilterHook", method).setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH).intercept { chain ->
                val instance = checkNotNull(chain.thisObject)
                synchronized(activeAdapters) { activeAdapters[instance] = true }
                val scope = FeedScope.fromEventType(eventType.invoke(pageParams.get(instance)) as String?)
                HookRuntime.count("FeedFilterHook", "Adapter作用域:$scope")
                val rules = policy()
                if (!rules.applies(scope)) return@intercept chain.proceed()
                val args = chain.args.toTypedArray()
                when (method) {
                    setData -> {
                        val items = args[0] as List<*>?
                        if (items != null) args[0] = filter(items, rules, scope, reader)
                    }
                    recommendBatch -> {
                        val items = args[2] as List<*>?
                        if (items != null) {
                            val accepted = filter(items, rules, scope, reader)
                            if (accepted.isEmpty()) return@intercept null
                            args[1] = accepted.size
                            args[2] = accepted
                        }
                    }
                    batch -> {
                        val source = args[2] as List<*>?
                        val start = args[0] as Int
                        val length = args[1] as Int
                        val currentCount = count.invoke(instance) as Int
                        if (source == null || source.isEmpty() || start !in source.indices ||
                            start > currentCount || length <= 0) return@intercept chain.proceed()
                        // start 同时是来源切片起点和目标插入位置；不能过滤整表后复用原索引。
                        require(length <= source.size - start) { "Adapter 区间插入超出来源列表" }
                        val accepted = filter(source.subList(start, start + length), rules, scope, reader)
                        if (accepted.isEmpty()) return@intercept null
                        args[1] = accepted.size
                        args[2] = ArrayList<Any?>().apply {
                            addAll(source.subList(0, start))
                            addAll(accepted)
                        }
                    }
                    else -> {
                        val item = args[1]
                        val reason = item?.let { rules.reason(reader.read(it, scope), scope) }
                        if (reason != null) {
                            HookRuntime.count("FeedFilterHook", "拒绝:$reason")
                            val position = args[0] as Int
                            if (method != insert && position >= 0 && position < count.invoke(instance) as Int) {
                                remove.invoke(instance, position, null)
                                notify.invoke(instance)
                            }
                            return@intercept null
                        }
                    }
                }
                chain.proceed(args)
            }
        }
        HookRuntime.subscribe("FeedFilterHook") { snapshot ->
            if (snapshot != null) {
                val rules = policy(snapshot)
                val instances = synchronized(activeAdapters) { activeAdapters.keys.toList() }
                for (instance in instances) {
                    // 推荐页由共享数据模型发送删除事件，不能绕过模型直接重设 Adapter。
                    if (recommendAdapter.isInstance(instance)) continue
                    val scope = FeedScope.fromEventType(eventType.invoke(pageParams.get(instance)) as String?)
                    if (!rules.applies(scope)) continue
                    val source = getData.invoke(instance) as List<*>
                    val accepted = filter(source, rules, scope, reader)
                    if (accepted.size != source.size) setData.invoke(instance, accepted)
                }
            }
        }
    }

    /**
     * 在模型变更和通知发送前执行跨页面广告规则及推荐内容规则，保持数据与界面事件一致。
     * @param module libxposed 模块。
     * @param loader 宿主最终类加载器。
     * @param reader 已验证的 Aweme 模型读取器。
     * @return Unit；配置变化通过模型的 deleteItems 通知链请求移除已加载的不合规内容。
     * Callers: init。
     */
    private fun hookRepositories(module: XposedModule, loader: ClassLoader, reader: FeedModelReader) {
        val model = HostSymbols.resolve(loader, HostSymbol.RECOMMEND_MODEL)
        val listModel = generateSequence<Class<*>>(model.superclass) { it.superclass }.single { type ->
            type.declaredMethods.any {
                it.name == "getItems" && it.parameterCount == 0 && it.returnType == List::class.java &&
                    java.lang.reflect.Modifier.isAbstract(it.modifiers)
            }
        }
        hookSharedModelAds(module, listModel, reader)
        val activeModels = java.util.WeakHashMap<Any, Boolean>()
        HookRuntime.onDispose("FeedFilterHook") { synchronized(activeModels) { activeModels.clear() } }
        val getItems = listModel.getDeclaredMethod("getItems").apply { isAccessible = true }
        val deleteItems = listModel.getDeclaredMethod("deleteItems", List::class.java).apply { isAccessible = true }
        val intType = Int::class.javaPrimitiveType!!
        val insertItems = model.getDeclaredMethod("insertItemList", List::class.java, intType).apply {
            check(returnType == Boolean::class.javaPrimitiveType) { "推荐列表插入方法必须返回 boolean" }
        }
        val writes = linkedMapOf(
            model.getDeclaredMethod("setItems", List::class.java) to 0,
            model.getDeclaredMethod(HostSymbols.member(HostSymbol.RECOMMEND_MODEL, "append"), List::class.java) to 0,
            model.getDeclaredMethod(HostSymbols.member(HostSymbol.RECOMMEND_MODEL, "update"), List::class.java, Boolean::class.javaPrimitiveType) to 0,
            insertItems to 0,
            model.getDeclaredMethod(HostSymbols.member(HostSymbol.RECOMMEND_MODEL, "batch"), intType, intType, List::class.java) to 2
        )
        for (constructor in listModel.declaredConstructors) {
            module.trackHook("FeedFilterHook", constructor).intercept { chain ->
                val result = chain.proceed()
                synchronized(activeModels) { activeModels[checkNotNull(chain.thisObject)] = true }
                result
            }
        }
        for ((method, argument) in writes) {
            method.isAccessible = true
            module.trackHook("FeedFilterHook", method).intercept { chain ->
                val rules = policy()
                val items = chain.args[argument] as List<*>?
                if (items == null || !rules.applies(FeedScope.FOR_YOU)) return@intercept chain.proceed()
                val args = chain.args.toTypedArray()
                val accepted = filter(items, rules, FeedScope.FOR_YOU, reader)
                // 全部拒绝时不发送空插入事件，也不向插卡调用方报告插入成功。
                if (method == insertItems && items.isNotEmpty() && accepted.isEmpty()) return@intercept false
                args[argument] = accepted
                HookRuntime.count("FeedFilterHook", "推荐数据模型准入")
                chain.proceed(args)
            }
        }
        HookRuntime.subscribe("FeedFilterHook") { snapshot ->
            if (snapshot != null) {
                val rules = policy(snapshot)
                val instances = synchronized(activeModels) { activeModels.keys.toList() }
                var requested = 0
                for (instance in instances) {
                    val scope = if (model.isInstance(instance)) FeedScope.FOR_YOU else FeedScope.OUTSIDE_FEED
                    if (!rules.applies(scope)) continue
                    val source = getItems.invoke(instance) as List<*>? ?: continue
                    val rejected = source.filterNotNull().filter {
                        reader.awemeType.isInstance(it) && rules.reject(reader.read(it, scope), scope)
                    }
                    if (rejected.isNotEmpty()) {
                        deleteItems.invoke(instance, rejected)
                        requested += rejected.size
                    }
                }
                HookRuntime.count("FeedFilterHook", "配置更新请求移除内容", requested.toLong())
                Log.i(TAG, "配置重检 revision=${snapshot.revision} models=${instances.size} requested=$requested")
            }
        }
    }

    /**
     * 在共享列表模型插入前拒绝广告，保持作者主页等页面的数据索引与显示列表一致。
     * @param module LSPosed 模块。
     * @param listModel 通过已解析推荐模型的继承关系定位的通用列表父类。
     * @param reader 已验证的 Aweme 模型读取器；其他模型的条目不进入广告规则。
     * @return Unit；被全部拒绝的插入返回 false，不修改模型或发送插入通知。
     * Callers: hookRepositories。
     */
    private fun hookSharedModelAds(module: XposedModule, listModel: Class<*>, reader: FeedModelReader) {
        val intType = Int::class.javaPrimitiveType!!
        val single = listModel.getDeclaredMethod("insertItem", Any::class.java, intType)
        val batch = listModel.getDeclaredMethod("insertItemList", List::class.java, intType)
        for (method in listOf(single, batch)) {
            check(method.returnType == Boolean::class.javaPrimitiveType) { "列表模型插入必须返回 boolean: $method" }
            method.isAccessible = true
            module.trackHook("FeedFilterHook", method).intercept { chain ->
                val rules = policy()
                if (!rules.removeAds) return@intercept chain.proceed()
                val source = if (method == single) listOf(chain.args[0]) else chain.args[0] as List<*>?
                if (source.isNullOrEmpty()) return@intercept chain.proceed()
                val accepted = source.filterNot { item ->
                    item != null && reader.awemeType.isInstance(item) &&
                        rules.reject(reader.read(item, FeedScope.OUTSIDE_FEED), FeedScope.OUTSIDE_FEED)
                }
                val rejected = source.size - accepted.size
                if (rejected == 0) return@intercept chain.proceed()
                HookRuntime.count("FeedFilterHook", "模型拒绝:广告", rejected.toLong())
                if (accepted.isEmpty()) return@intercept false
                val args = chain.args.toTypedArray()
                args[0] = accepted
                chain.proceed(args)
            }
        }
    }
}
