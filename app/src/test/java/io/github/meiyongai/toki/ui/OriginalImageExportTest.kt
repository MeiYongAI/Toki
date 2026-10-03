package io.github.meiyongai.toki.ui

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.*
import org.junit.Test

/** 验证图片原样复制、资源释放以及错误向界面传播的契约。 */
class OriginalImageExportTest {
    /** 验证跨多个缓冲区复制不改变图片字节。@return Unit。Callers: JUnit。 */
    @Test fun originalBytesArePreserved() {
        val bytes = ByteArray(111327) { (it % 256).toByte() }
        val output = ByteArrayOutputStream()
        assertEquals(bytes.size.toLong(), exportOriginalImage({ ByteArrayInputStream(bytes) }, { output }))
        assertArrayEquals(bytes, output.toByteArray())
    }

    /** 验证不可写目标不会被误报为成功。@return Unit。Callers: JUnit。 */
    @Test fun missingOutputClosesSourceAndReportsError() {
        var closed = false
        val input = object : ByteArrayInputStream(byteArrayOf(1)) {
            /** 记录资源释放。@return Unit。Callers: exportOriginalImage。 */
            override fun close() { closed = true; super.close() }
        }
        val error = assertThrows(IOException::class.java) { exportOriginalImage({ input }, { null }) }
        assertEquals("无法打开所选保存位置", error.message)
        assertTrue(closed)
    }

    /** 验证写入失败释放目标并保留原始错误。@return Unit。Callers: JUnit。 */
    @Test fun writeFailureIsNotHidden() {
        var closed = false
        val failure = IOException("空间不足")
        val output = object : OutputStream() {
            /** 模拟存储失败。@param value 待写字节。@return 不返回，抛出错误。Callers: copyTo。 */
            override fun write(value: Int) { throw failure }
            /** 记录目标关闭。@return Unit。Callers: exportOriginalImage。 */
            override fun close() { closed = true }
        }
        assertSame(failure, assertThrows(IOException::class.java) {
            exportOriginalImage({ ByteArrayInputStream(byteArrayOf(1)) }, { output })
        })
        assertTrue(closed)
    }

    /** 验证关闭目标时发生的提交错误不能确认成功。@return Unit。Callers: JUnit。 */
    @Test fun closeFailureIsReported() {
        val output = object : ByteArrayOutputStream() {
            /** 模拟目标提交失败。@return 不返回，抛出错误。Callers: exportOriginalImage。 */
            override fun close() { throw IOException("提交失败") }
        }
        assertThrows(IOException::class.java) {
            exportOriginalImage({ ByteArrayInputStream(byteArrayOf(1)) }, { output })
        }
    }

    /** 验证权限错误保留类型供界面给出明确提示。@return Unit。Callers: JUnit。 */
    @Test fun permissionFailureIsReported() {
        assertThrows(SecurityException::class.java) {
            exportOriginalImage({ ByteArrayInputStream(byteArrayOf(1)) }, { throw SecurityException("拒绝写入") })
        }
    }
}
