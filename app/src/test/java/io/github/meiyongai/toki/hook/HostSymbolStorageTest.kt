package io.github.meiyongai.toki.hook

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 使用生产路径函数及真实临时文件验证符号结果的存储生命周期和目录隔离。 */
class HostSymbolStorageTest {
    @get:Rule val temporary = TemporaryFolder()

    /** 验证清空宿主缓存目录后，查找结果仍可从同一生产路径读取。@return Unit。Callers: JUnit。 */
    @Test fun clearingHostCachePreservesSymbols() {
        val data = temporary.newFolder("host")
        val cache = File(data, "cache")
        assertTrue(cache.mkdir())
        val disposable = File(cache, "temporary-entry")
        disposable.writeText("cache data")
        val symbols = HostSymbols.storageFile(data)
        symbols.writeText("verified symbols")

        assertTrue(disposable.delete())
        assertTrue(cache.delete())

        assertEquals("verified symbols", HostSymbols.storageFile(data).readText())
        assertEquals(File(data, "no_backup/toki").canonicalFile, checkNotNull(symbols.parentFile).canonicalFile)
        assertFalse(cache.exists())
    }

    /** 验证重复获取路径不修改已保存的查找结果。@return Unit。Callers: JUnit。 */
    @Test fun repeatedAccessPreservesExistingResult() {
        val data = temporary.newFolder("host")
        val symbols = HostSymbols.storageFile(data)
        symbols.writeText("cache.key=verified\nTARGET=X.Target")
        assertEquals(symbols, HostSymbols.storageFile(data))
        assertEquals("cache.key=verified\nTARGET=X.Target", symbols.readText())
    }

    /** 验证并发初始化允许共享同一个结果目录，异常通过 Future 传播。@return Unit。Callers: JUnit。 */
    @Test fun concurrentDirectoryCreationIsIdempotent() {
        val data = temporary.newFolder("host")
        val start = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(4)
        try {
            val requests = List(4) {
                workers.submit<File> {
                    check(start.await(5, TimeUnit.SECONDS)) { "并发测试启动超时" }
                    HostSymbols.storageFile(data)
                }
            }
            start.countDown()
            val results = requests.map { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, results.toSet().size)
            assertTrue(checkNotNull(results.first().parentFile).isDirectory)
        } finally {
            workers.shutdownNow()
        }
    }

    /** 验证不同宿主数据目录的结果彼此隔离。@return Unit。Callers: JUnit。 */
    @Test fun separateHostDirectoriesDoNotShareResults() {
        val first = HostSymbols.storageFile(temporary.newFolder("first"))
        first.writeText("first host")
        val second = HostSymbols.storageFile(temporary.newFolder("second"))
        assertNotEquals(first.canonicalFile, second.canonicalFile)
        assertFalse(second.exists())
        assertEquals("first host", first.readText())
    }

    /** 验证目录被普通文件占用时明确失败，不改用缓存目录。@return Unit。Callers: JUnit。 */
    @Test fun occupiedDirectoryIsRejected() {
        val data = temporary.newFolder("host")
        File(data, "no_backup").writeText("not a directory")
        val error = assertThrows(IllegalStateException::class.java) { HostSymbols.storageFile(data) }
        assertTrue(error.message.orEmpty().contains("无法创建宿主符号结果目录"))
        assertFalse(File(data, "cache").exists())
    }
}
