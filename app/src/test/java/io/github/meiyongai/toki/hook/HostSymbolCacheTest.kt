package io.github.meiyongai.toki.hook

import android.util.AtomicFile
import java.io.File
import java.io.OutputStream
import java.util.Properties
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 验证 AtomicFile 并发读取的实际干扰及生产访问锁的完整事务。 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35], shadows = [PosixAtomicFileShadow::class])
class HostSymbolCacheTest {
    @get:Rule val temporary = TemporaryFolder()

    /** 构造测试属性。@param key 缓存身份。@return 属性。Callers: 本类测试。 */
    private fun value(key: String) = Properties().apply { setProperty("cache.key", key) }

    /**
     * 复现无访问锁时，另一个读取者删除尚未提交的新版本。
     * 序列化后关闭流以允许 Windows 删除文件；此时仍未执行 AtomicFile 提交。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun uncoordinatedAtomicReadInvalidatesPendingReplacement() {
        val file = File(temporary.root, "symbols")
        HostSymbolCache.write(file, value("first"))
        val writer = AtomicFile(file)
        writer.startWrite().use { value("second").store(it, null) }
        val pending = File(file.path + ".new")
        assertTrue(pending.isFile)
        val concurrent = AtomicFile(file).openRead().use { Properties().apply { load(it) } }
        assertEquals("first", concurrent.getProperty("cache.key"))
        assertFalse(pending.exists())
        assertEquals("first", HostSymbolCache.read(file).getProperty("cache.key"))
    }

    /**
     * 验证写入暂停期间，统一入口的读取等待完整提交，不能触碰未提交的事务文件。
     * @return Unit；无入参。工作线程异常通过 Future 传播。
     * Callers: JUnit。
     */
    @Test fun readerWaitsForCommitAndVerification() {
        val file = File(temporary.root, "symbols")
        HostSymbolCache.write(file, value("first"))
        val writing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val reading = CountDownLatch(1)
        val next = object : Properties() {
            /** 阻塞序列化以稳定重现并发读写。@param out 输出流。@param comments 注释。@return Unit。Callers: HostSymbolCache.write。 */
            override fun store(out: OutputStream, comments: String?) {
                writing.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                super.store(out, comments)
            }
        }.apply { setProperty("cache.key", "second") }
        val workers = Executors.newFixedThreadPool(2)
        try {
            val write = workers.submit { HostSymbolCache.write(file, next) }
            assertTrue(writing.await(5, TimeUnit.SECONDS))
            val read = workers.submit<Properties> { reading.countDown(); HostSymbolCache.read(file) }
            assertTrue(reading.await(5, TimeUnit.SECONDS))
            assertFalse(read.isDone)
            release.countDown()
            write.get(5, TimeUnit.SECONDS)
            assertEquals(next, read.get(5, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            workers.shutdownNow()
        }
    }

    /** 首次使用为空集合，已保存结果可原样读取。@return Unit；无入参。Callers: JUnit。 */
    @Test fun absentFileAndRoundTripAreDistinct() {
        val file = File(temporary.root, "symbols")
        assertTrue(HostSymbolCache.read(file).isEmpty)
        val expected = value("verified").apply { setProperty("member", "X.Target#method") }
        HostSymbolCache.write(file, expected)
        assertEquals(expected, HostSymbolCache.read(file))
    }

    /** 非法文件路径不被当作没有缓存。@return Unit；无入参。Callers: JUnit。 */
    @Test fun directoryIsReportedAsError() {
        val file = temporary.newFolder("symbols")
        assertThrows(java.io.IOException::class.java) { HostSymbolCache.read(file) }
    }

    /** 提交异常必须保留已确认结果并原样传播，之后仍可正常读写。@return Unit；无入参。Callers: JUnit。 */
    @Test fun failedWritePreservesCommittedResultAndReleasesLock() {
        val file = File(temporary.root, "symbols")
        val original = value("first")
        HostSymbolCache.write(file, original)
        val failure = java.io.IOException("模拟序列化失败")
        val invalid = object : Properties() {
            /** 模拟部分写入后失败。@param out 输出流。@param comments 注释。@return 不返回，抛出测试异常。Callers: HostSymbolCache.write。 */
            override fun store(out: OutputStream, comments: String?) {
                out.write("partial".toByteArray())
                throw failure
            }
        }
        assertSame(failure, assertThrows(java.io.IOException::class.java) { HostSymbolCache.write(file, invalid) })
        assertEquals(original, HostSymbolCache.read(file))
        HostSymbolCache.write(file, value("second"))
        assertEquals(value("second"), HostSymbolCache.read(file))
    }
}
