package io.github.meiyongai.toki.hook

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Properties

/** 验证发布清单的代码边界、规则边界与完整性，防止无扫描路径误启用。 */
class HostBundledProfilesTest {
    private val rules = "SETTINGS\tmethod-v1\t()I\tname:read\tvalue\n"

    /** @return 完整已封存的测试清单。无参数。Callers: 本类测试。 */
    private fun profile(): Properties = HostScanPlan(Properties(), rules, "code", "format", setOf(HostSymbol.SETTINGS))
        .merge(Properties().apply {
            setProperty("SETTINGS", "X.A")
            setProperty("member.SETTINGS.value", "read()I")
        }).apply { setProperty("profile.checksum", HostProfileManifest.checksum(this)) }

    /** @param data 输入清单。@param code 代码身份。@param source 规则。@return 读取结果。Callers: 本类测试。 */
    private fun read(data: Properties, code: String = "code", source: String = rules): Properties {
        val bytes = ByteArrayOutputStream().also { data.store(it, null) }.toByteArray()
        return HostBundledProfiles.read(bytes.inputStream(), code, source, "format")
    }

    /** 无参数、无返回。Callers: JUnit；只接受当前代码和规则。 */
    @Test fun acceptsExactBuildAndRejectsAddedCodeOrChangedRules() {
        val data = profile()
        assertEquals(data, read(data))
        assertThrows(IllegalStateException::class.java) { read(data, "code-with-another-split") }
        assertThrows(IllegalStateException::class.java) { read(data, source = rules.replace("()I", "()Z")) }
    }

    /** 无参数、无返回。Callers: JUnit；移除成员、修改类名、矛盾结果均不可使用。 */
    @Test fun rejectsCorruptMembersAndContradictoryResults() {
        val missing = profile().apply { remove("member.SETTINGS.value") }
        assertThrows(IllegalStateException::class.java) { read(missing) }
        val changed = profile().apply { setProperty("SETTINGS", "X.B") }
        assertThrows(IllegalStateException::class.java) { read(changed) }
        val contradictory = profile().apply {
            setProperty("error.SETTINGS", "ambiguous")
            setProperty("profile.checksum", HostProfileManifest.checksum(this))
        }
        assertThrows(IllegalStateException::class.java) { read(contradictory) }
    }

    /** 无参数、无返回。Callers: JUnit；未知完整构建必须进入正常发现流程。 */
    @Test fun unknownBuildHasNoBundledResult() {
        assertNull(HostBundledProfiles.load("0".repeat(64), rules))
    }

    /** 无参数、无返回。Callers: JUnit；发布的每份资源必须与当前规则及清单格式一致。 */
    @Test fun shippedProfilesMatchCurrentRules() {
        val source = checkNotNull(javaClass.getResourceAsStream("/toki-host-rules.tsv")).bufferedReader().use { it.readText() }
        val directory = java.io.File("src/main/resources/toki-host-profiles")
        val profiles = checkNotNull(directory.listFiles()).filter { it.extension == "properties" }
        assertTrue(profiles.isNotEmpty())
        profiles.forEach { file ->
            val loaded = file.inputStream().use { HostBundledProfiles.read(it, file.nameWithoutExtension, source, HostSymbols.INDEX_FORMAT) }
            assertTrue(HostScanPlan(loaded, source, file.nameWithoutExtension, HostSymbols.INDEX_FORMAT,
                HostSymbol.entries.toSet()).pending.isEmpty())
        }
    }
}
