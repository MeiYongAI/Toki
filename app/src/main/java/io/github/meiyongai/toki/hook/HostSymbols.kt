package io.github.meiyongai.toki.hook

import android.util.Log

/** 宿主逻辑符号；所有官方构建统一按实际代码指纹和成员契约解析。 */
internal enum class HostSymbol {
    DOWNLOAD_SOURCE,
    LAYOUT_TOP_TABS,
    LAYOUT_TOOLBAR,
    LAYOUT_BOTTOM_ITEM,
    COMMENT_TRANSLATION,
    MENU_CAPTION_ACL,
    CAPTION_CONSUMER,
    TRANSLATION_REVERSE,
    BACKGROUND_AUDIO,
    SPEED_MANAGER,
    PLAYER_CONTROLLER,
    PLAYER_MANAGER,
    SETTINGS,
    SEARCH_AUTO_SCROLL,
    AUTO_SCROLL_MENU,
    AUTO_SCROLL_PLAYBACK,
    AUTO_SCROLL_CONTEXT,
    AUTO_SCROLL_REGISTRATION,
    AUTO_SCROLL_REGISTER,
    COLD_FEED,
    PRELOADED_FEED,
    DARK_LAYER,
    SEEK_BAR,
    SEEK_CONTROLLER,
    PLAY_BUTTON,
    OFFLINE_RECOVERY,
    AUTHOR_LOCATION,
    RESERVED_AREA,
    FEED_ADAPTION,
    PHOTO_LAYOUT,
    FEED_ADAPTER,
    RECOMMEND_MODEL,
    RECOMMEND_ADAPTER,
    COMMENT_COPY,
    SPEED_OPTIONS,
    MUTE_INFO,
    VIDEO_CELL,
}

/** 以完整代码集合和规则摘要验证私有持久结果，唯一匹配后才允许宿主符号注册。 */
internal object HostSymbols {
    internal const val INDEX_FORMAT = "index-v9"
    private var resolved = java.util.Properties()
    private val rules: String by lazy {
        checkNotNull(HostSymbols::class.java.getResourceAsStream("/toki-host-rules.tsv")) {
            "模块缺少 DEX 特征规则"
        }.bufferedReader().use { it.readText() }
    }

    /**
     * 读取私有持久结果并验证代码与规则；未准备好时扫描，保存核对后提示用户手动重启。
     * @param info 宿主基础包、全部 Split 和私有数据目录。
     * @param scanAllowed 仅主进程允许生成缓存，避免多进程同时扫描。
     * @return 是否已经存在与完整代码集合及规则一致的缓存。
     * Callers: TokiModule.onPackageReady。
     */
    fun initialize(info: android.content.pm.ApplicationInfo, scanAllowed: Boolean, required: Set<HostSymbol>): Boolean {
        try {
            return prepare(info, scanAllowed, required)
        } catch (error: Exception) {
            // 同步准备位于框架生命周期内，记录可见状态后仍保留原始异常和严格契约。
            HookRuntime.failure("HostSymbols", error, "适配准备失败")
            throw error
        }
    }

    /**
     * 统一选择本地或随模块发布的已验证结果，未知代码集合才进入原生检索。
     * @param info 完整代码路径和私有目录。
     * @param scanAllowed 是否由当前主进程负责生成适配结果。
     * @param required 已启用功能的依赖。
     * @return 结果是否已可用于当前进程注册。
     * Callers: initialize。
     */
    private fun prepare(info: android.content.pm.ApplicationInfo, scanAllowed: Boolean, required: Set<HostSymbol>): Boolean {
        require(required.isNotEmpty()) { "无需符号适配的会话不能启动扫描" }
        val paths = listOf(info.sourceDir) + info.splitSourceDirs.orEmpty()
        val identity = HostDexIndex.identity(paths)
        val rulesIdentity = HostDexIndex.digest(rules)
        val key = HostDexIndex.digest("$INDEX_FORMAT\n$identity\n" + rulesIdentity)
        val cache = storageFile(java.io.File(info.dataDir))
        val local = HostSymbolCache.read(cache)
        val bundled = if (HostScanPlan(local, rules, identity, INDEX_FORMAT, required).pending.isEmpty()) null
            else HostBundledProfiles.load(identity, rules)
        val stored = bundled ?: local
        val plan = HostScanPlan(stored, rules, identity, INDEX_FORMAT, required)
        if (plan.pending.isEmpty()) {
            if (bundled != null && scanAllowed) HostSymbolCache.write(cache, bundled)
            resolved = if (stored.getProperty("cache.format") == INDEX_FORMAT && stored.getProperty("cache.key") == key) stored else {
                plan.merge(java.util.Properties()).apply {
                    for (field in listOf("cache.scanPid", "cache.createdAt", "cache.reason")) {
                        stored.getProperty(field)?.let { setProperty(field, it) }
                    }
                    if (scanAllowed) HostSymbolCache.write(cache, this)
                }
            }
            val failures = resolved.stringPropertyNames().filter { it.startsWith("error.") }
            HookRuntime.adaptation("适配缓存有效\n代码标识：$identity\n未匹配目标：${failures.joinToString().ifEmpty { "无" }}")
            HookRuntime.state("HostSymbols", if (bundled != null) "内置适配已验证" else "缓存已验证")
            if (bundled != null) HookRuntime.event("TokiHostSymbols", "内置适配直接启用 code=$identity；无需扫描或重启")
            HookRuntime.event("TokiHostSymbols", "缓存已验证 pid=${android.os.Process.myPid()} key=$key scanPid=${stored.getProperty("cache.scanPid")} code=$identity rules=$rulesIdentity")
            return true
        }
        val reason = "${HostCacheReason.describe(stored, identity, rulesIdentity)}；补查目标：${plan.pending.joinToString { it.name }}"
        HookRuntime.event("TokiHostSymbols", "需要查找：$reason；pid=${android.os.Process.myPid()} allowed=$scanAllowed oldKey=${stored.getProperty("cache.key")} newKey=$key code=$identity rules=$rulesIdentity apkCount=${paths.size}")
        val targets = plan.pending
        val selectedRules = selectRules(rules, targets)
        HookRuntime.adaptation(if (scanAllowed) "正在查找 ${targets.size} 项功能依赖 · $identity" else "等待主进程扫描完成后重启")
        HookRuntime.state("HostSymbols", if (scanAllowed) "正在扫描" else "等待主进程准备")
        if (scanAllowed) {
            HostScanController.session.begin()
            Thread({
                try {
                    val scanned = HostNativeIndex.scan(paths, selectedRules) { completed, total ->
                        HostScanController.session.progress(completed, total)
                    }
                    HostScanController.session.saving()
                    check(HostDexIndex.identity(paths) == identity) { "扫描期间宿主代码集合发生变化，缓存未发布" }
                    val result = plan.merge(scanned)
                    result.setProperty("cache.key", key)
                    result.setProperty("cache.identity", identity)
                    result.setProperty("cache.rules", rulesIdentity)
                    result.setProperty("cache.scanPid", android.os.Process.myPid().toString())
                    result.setProperty("cache.createdAt", System.currentTimeMillis().toString())
                    result.setProperty("cache.reason", reason)
                    HostSymbolCache.write(cache, result)
                    HookRuntime.event("TokiHostSymbols", "结果保存并核对成功 pid=${android.os.Process.myPid()} key=$key")
                    val failures = result.stringPropertyNames().filter { it.startsWith("error.") }
                    HookRuntime.adaptation("扫描结果已保存，请手动重启 TikTok\n代码标识：$identity\n未匹配目标：${failures.joinToString().ifEmpty { "无" }}")
                    HookRuntime.state("HostSymbols", "扫描完成，等待重新打开")
                    HostScanController.session.ready()
                    HostScanController.onScanFinished()
                } catch (error: Exception) {
                    HookRuntime.event("TokiHostSymbols", "扫描未完成 pid=${android.os.Process.myPid()} key=$key error=${error.javaClass.name}")
                    HookRuntime.failure("HostSymbols", error, "扫描失败，未发布缓存")
                    HookRuntime.adaptation("适配扫描失败：${error.javaClass.simpleName}；请检查 TokiHostSymbols 日志")
                    Log.e("TokiHostSymbols", "宿主符号扫描失败", error)
                    HostScanController.session.fail()
                    HostScanController.onScanFinished()
                }
            }, "TokiSymbolScan").apply { priority = Thread.MIN_PRIORITY }.start()
        }
        return false
    }

    /** 明确记录已扫描的覆盖范围；缺失/非法字段是缓存契约错误。 */
    internal fun coverage(properties: java.util.Properties): Set<HostSymbol> {
        val names = checkNotNull(properties.getProperty("cache.symbols")) { "适配缓存缺少符号覆盖范围" }
        require(names.isNotBlank()) { "适配缓存符号覆盖范围为空" }
        return names.split(',').mapTo(linkedSetOf()) { HostSymbol.valueOf(it) }
    }

    /** 判定所需目标是否均有确定扫描结果；未匹配结果由功能注册阶段明确报告。
     * @param properties 已验证代码和规则身份的缓存。
     * @param coverage 已扫描目标集合。
     * @param required 当前功能依赖。
     * @return 每个目标恰有成功类名或失败原因时为 true；缺失或矛盾结果为 false。
     * Callers: prepare、HostFeaturePlanTest。
     */
    internal fun cacheComplete(
        properties: java.util.Properties,
        coverage: Set<HostSymbol>,
        required: Set<HostSymbol>,
    ): Boolean = required.all { symbol ->
        coverage.contains(symbol) &&
            (!properties.getProperty(symbol.name).isNullOrBlank() xor
                !properties.getProperty("error.${symbol.name}").isNullOrBlank())
    }

    /** 规则匹配只处理功能依赖；关联指纹仍从候选实际引用中解析。 */
    internal fun selectRules(source: String, required: Set<HostSymbol>): String {
        require(required.isNotEmpty()) { "必须声明扫描目标" }
        val names = required.mapTo(hashSetOf()) { it.name }
        val selected = source.lineSequence().filter { it.substringBefore('\t') in names }.toList()
        val present = selected.mapTo(hashSetOf()) { it.substringBefore('\t') }
        check(present.containsAll(names)) { "模块缺少依赖的特征规则：${names - present}" }
        return selected.joinToString("\n", postfix = "\n")
    }

    /**
     * 并发安全地创建不参与备份、不会随宿主缓存清理的唯一目录，并返回结果文件位置。
     * 初始化早于 Application Context 可用时机；目录结构与 Android ContextImpl 的
     * getNoBackupFilesDir 一致。清除宿主应用数据或卸载宿主会删除此目录。
     * @param dataDirectory ApplicationInfo 提供的宿主私有数据目录。
     * @return 交由 AtomicFile 读写的符号结果文件。
     * @throws IllegalStateException 目录创建失败或路径被普通文件占用。
     * Callers: HostSymbols.initialize、HostSymbolStorageTest。
     */
    internal fun storageFile(dataDirectory: java.io.File): java.io.File {
        val directory = java.io.File(dataDirectory, "no_backup/toki")
        check(directory.mkdirs() || directory.isDirectory) { "无法创建宿主符号结果目录：$directory" }
        return java.io.File(directory, "toki-host-symbols.properties")
    }

    /**
     * 返回唯一候选的原始类名；缺失或歧义必须由功能注册事务明确报告。
     * @param symbol 业务符号。
     * @return DEX 二进制类名。
     * Callers: 各业务 Hook。
     */
    fun name(symbol: HostSymbol): String = checkNotNull(resolved.getProperty(symbol.name)) {
        "${symbol.name}: ${resolved.getProperty("error.${symbol.name}", "尚未扫描")}"
    }

    /**
     * 按业务角色读取已通过完整 DEX 契约验证的成员名称，不依据渠道或版本猜测。
     * @param symbol 方法或字段所属的业务符号。
     * @param role 规则中声明的业务成员角色。
     * @return 当前代码集合中对应的实际方法名或字段名。
     * Callers: FeedFilterHook、AutoScrollHook、AutoCleanModeHook、ImmersiveFullScreenHook、
     * ProgressBarHook、PlaybackSpeedHook、MusicUnlockHook。
     */
    fun member(symbol: HostSymbol, role: String): String {
        name(symbol)
        return checkNotNull(resolved.getProperty("member.${symbol.name}.$role")) {
            "${symbol.name}.$role: 缺少已验证的成员契约"
        }.substringBefore('(').substringBefore(':')
    }

    /**
     * 读取同一行为角色下的完整成员引用集合，数量由实际代码决定。
     * @param symbol 业务符号。
     * @param role 已验证的成员集合角色。
     * @return 非空、无重复的完整 DEX 成员引用。
     * Callers: PlaybackSpeedHook.menuContract、CommentCopyHook.init。
     */
    fun members(symbol: HostSymbol, role: String): List<String> {
        name(symbol)
        val encoded = checkNotNull(resolved.getProperty("members.${symbol.name}.$role")) {
            "${symbol.name}.$role: 缺少已验证的成员集合"
        }
        val members = encoded.split('\n')
        check(members.all { it.isNotBlank() } && members.distinct().size == members.size) {
            "${symbol.name}.$role: 成员集合为空或重复"
        }
        return members
    }

    /** 检查符号是否唯一解析。@param symbol 业务符号。@return 是否可用。Callers: PlaybackSpeedHook。 */
    fun available(symbol: HostSymbol): Boolean = resolved.containsKey(symbol.name)

    /**
     * 使用最终类加载器获取宿主类，不触发静态初始化。
     * @param loader 宿主最终类加载器。
     * @param symbol 业务符号。
     * @return 经扫描定位的 Class。
     * Callers: 各业务 Hook.init。
     */
    fun resolve(loader: ClassLoader, symbol: HostSymbol): Class<*> = Class.forName(name(symbol), false, loader)
}
