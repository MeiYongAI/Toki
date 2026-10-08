package io.github.meiyongai.toki.hook

import java.io.InputStream
import java.util.Properties

/** 随模块分发的离线适配结果，只有完整代码身份及全部规则一致才可启用。 */
internal object HostBundledProfiles {
    /**
     * 查找当前完整代码集合的预计算结果；没有此构建时返回 null。
     * @param identity 包含全部代码 Split 的代码身份。
     * @param rules 当前完整规则文本。
     * @return 经严格校验的预计算结果，未知构建为 null；损坏资源直接报错。
     * Callers: HostSymbols.prepare。
     */
    fun load(identity: String, rules: String): Properties? {
        require(identity.matches(Regex("[0-9a-f]{64}"))) { "无效代码身份" }
        return javaClass.getResourceAsStream("/toki-host-profiles/$identity.properties")?.use {
            read(it, identity, rules, HostSymbols.INDEX_FORMAT)
        }
    }

    /**
     * 验证发布结果的完整代码、规则、格式、覆盖范围和每个目标的确定结果。
     * @param input 预计算结果流，由调用方关闭。
     * @param identity 当前代码身份。
     * @param rules 当前全部规则。
     * @param format 扫描结果格式。
     * @return 完整且可直接注册的结果。
     * Callers: load、HostBundledProfilesTest。
     */
    internal fun read(input: InputStream, identity: String, rules: String, format: String): Properties {
        val result = Properties().apply { load(input) }
        check(result.getProperty("profile.checksum") == HostProfileManifest.checksum(result)) {
            "预计算适配结果完整性校验失败"
        }
        val digest = HostDexIndex.digest(rules)
        check(result.getProperty("cache.identity") == identity && result.getProperty("cache.format") == format &&
            result.getProperty("cache.rules") == digest &&
            result.getProperty("cache.key") == HostDexIndex.digest("$format\n$identity\n$digest")) {
            "预计算适配结果与代码或规则不一致"
        }
        val required = rules.lineSequence().filter { it.isNotBlank() && !it.startsWith('#') }
            .map { HostSymbol.valueOf(it.substringBefore('\t')) }.toSet()
        check(HostSymbols.coverage(result) == required && HostSymbols.cacheComplete(result, required, required)) {
            "预计算适配结果不完整"
        }
        return result
    }
}
