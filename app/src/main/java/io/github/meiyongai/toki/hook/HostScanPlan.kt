package io.github.meiyongai.toki.hook

import java.util.Properties

/** 按目标规则校验缓存；代码或扫描语义变化时全部失效。 */
internal class HostScanPlan(
    private val stored: Properties,
    private val source: String,
    private val identity: String,
    private val format: String,
    required: Set<HostSymbol>,
) {
    private val rules = HostDexIndex.digest(source)
    private val exact = stored.getProperty("cache.key") == HostDexIndex.digest("$format\n$identity\n$rules")
    private val sameCode = exact || (stored.getProperty("cache.identity") == identity &&
        stored.getProperty("cache.format") == format)
    val retained: Set<HostSymbol> = if (!sameCode) emptySet() else HostSymbols.coverage(stored).filterTo(linkedSetOf()) {
        HostSymbols.cacheComplete(stored, setOf(it), setOf(it)) &&
            (exact || stored.getProperty("cache.rule.${it.name}") == fingerprint(it))
    }
    val pending = required - retained

    /** @param symbol 查找目标。@return 该目标全部规则摘要，忽略行顺序和换行风格。Callers: 初始化、merge。 */
    private fun fingerprint(symbol: HostSymbol): String = HostDexIndex.digest(
        HostSymbols.selectRules(source, setOf(symbol)).lineSequence().filter { it.isNotBlank() }.sorted().joinToString("\n")
    )

    /**
     * 合并仍有效的结果及本次补查，删除失效目标的旧成员、集合和错误。
     * @param scanned 本次 pending 的完整扫描结果。
     * @return 可原子发布的新缓存，不修改输入缓存。
     * Callers: HostSymbols.prepare；HostScanPlanTest。
     */
    fun merge(scanned: Properties): Properties = Properties().apply {
        for (symbol in retained) {
            for (key in stored.stringPropertyNames()) {
                if (key == symbol.name || key == "error.${symbol.name}" ||
                    key.startsWith("member.${symbol.name}.") || key.startsWith("members.${symbol.name}.")) {
                    setProperty(key, stored.getProperty(key))
                }
            }
        }
        putAll(scanned)
        val covered = retained + pending
        check(HostSymbols.cacheComplete(this, covered, covered)) { "补查结果不完整，拒绝发布" }
        setProperty("cache.format", format)
        setProperty("cache.identity", identity)
        setProperty("cache.rules", rules)
        setProperty("cache.key", HostDexIndex.digest("$format\n$identity\n$rules"))
        setProperty("cache.symbols", covered.map { it.name }.sorted().joinToString(","))
        covered.forEach { setProperty("cache.rule.${it.name}", fingerprint(it)) }
    }
}
