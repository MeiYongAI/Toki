package io.github.meiyongai.toki.hook

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Bundle
import java.io.IOException
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 同步适配准备失败必须进入诊断，同时按原异常继续传播给框架。 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [35])
class HostSymbolsPreparationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Before @After fun resetDiagnostic() {
        HookRuntime.start("preparation-test") { _, _ -> }
        (HookRuntime::class.java.getDeclaredField("features").apply { isAccessible = true }
            .get(null) as Bundle).remove("HostSymbols")
    }

    private fun host(): ApplicationInfo {
        val apk = temporary.newFile("host.apk")
        ZipOutputStream(apk.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("classes.dex"))
            // 本测试只到身份头和缓存验证，不进入 DEX 解析或扫描。
            zip.write(ByteArray(32))
            zip.closeEntry()
        }
        return ApplicationInfo().apply {
            sourceDir = apk.absolutePath
            dataDir = temporary.newFolder("data").absolutePath
        }
    }

    private fun report(): Bundle {
        val snapshot = HookRuntime::class.java.getDeclaredMethod("snapshot").apply { isAccessible = true }
            .invoke(HookRuntime) as Bundle
        return requireNotNull(requireNotNull(snapshot.getBundle("features")).getBundle("HostSymbols"))
    }

    @Test fun synchronousFailureRetainsOriginalExceptionAndPublishesDiagnostic() {
        val host = host()
        val error = IOException("injected synchronous preparation failure")
        val before = HostScanController.session.status.phase
        // 现有同步事件出口替身在准备事件处失败，检验边界不会包装或替换错误。
        HookRuntime.start("preparation-test") { _, _ -> throw error }
        val actual = assertThrows(IOException::class.java) {
            HostSymbols.initialize(host, false, setOf(HostSymbol.COMMENT_TRANSLATION))
        }
        assertSame(error, actual)
        assertEquals("适配准备失败", report().getString("errorStatus"))
        assertTrue(report().getString("error").orEmpty().startsWith(IOException::class.java.name))
        assertEquals(before, HostScanController.session.status.phase)
    }

    @Test fun matchingCacheWithoutCoverageFailsVisiblyAndNeverStartsScanning() {
        val host = host()
        val identity = HostDexIndex.identity(listOf(host.sourceDir))
        val rules = javaClass.getResourceAsStream("/toki-host-rules.tsv")!!.bufferedReader().use { it.readText() }
        val key = HostDexIndex.digest("${HostSymbols.INDEX_FORMAT}\n$identity\n" + HostDexIndex.digest(rules))
        HostSymbolCache.write(HostSymbols.storageFile(java.io.File(host.dataDir)), Properties().apply {
            setProperty("cache.key", key)
            // 故意缺少当前格式必需的 cache.symbols；不把损坏缓存解释成首次扫描。
        })
        val before = HostScanController.session.status.phase
        val error = assertThrows(IllegalStateException::class.java) {
            HostSymbols.initialize(host, false, setOf(HostSymbol.COMMENT_TRANSLATION))
        }
        assertTrue(error.message.orEmpty().contains("符号覆盖范围"))
        assertEquals("适配准备失败", report().getString("errorStatus"))
        assertTrue(report().getString("error").orEmpty().startsWith(IllegalStateException::class.java.name))
        assertEquals(before, HostScanController.session.status.phase)
    }
}
