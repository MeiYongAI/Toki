package io.github.meiyongai.toki.hook

import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import java.util.Properties

/** 统一串行化宿主各进程的缓存读写，防止 AtomicFile 读取与提交相互干扰。 */
internal object HostSymbolCache {
    /**
     * 在独占文件锁内读取完整缓存；首次使用时返回空属性集合。
     * @param file 唯一缓存文件。
     * @return 实际保存的属性，读取错误原样传播。
     * Callers: HostSymbols.initialize、HostSymbolCacheTest。
     */
    fun read(file: File): Properties = access(file, ::readAtomic)

    /**
     * 在同一次独占访问中提交并重新读取核对，成功前不能发布完成结果。
     * @param file 唯一缓存文件。
     * @param properties 待保存的完整符号和身份元数据。
     * @return Unit；提交或核对失败明确抛出异常。
     * Callers: HostSymbols 扫描线程、HostSymbolCacheTest。
     */
    fun write(file: File, properties: Properties) = access(file) { atomic ->
        val stream = atomic.startWrite()
        try {
            properties.store(stream, "Toki verified symbols")
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
        check(readAtomic(atomic) == properties) { "适配缓存写入后校验不一致，不能发布完成结果" }
    }

    /**
     * 由 AtomicFile 处理事务恢复，仅将确实不存在的文件视为首次使用。
     * @param atomic 已持有访问锁的事务文件。
     * @return 完整属性；权限、格式和路径错误继续抛出。
     * Callers: read、write。
     */
    private fun readAtomic(atomic: AtomicFile): Properties {
        val stream = try {
            atomic.openRead()
        } catch (error: FileNotFoundException) {
            val file = atomic.baseFile
            if (file.exists() || File(file.path + ".bak").exists() || !checkNotNull(file.parentFile).canRead()) throw error
            return Properties()
        }
        return stream.use { Properties().apply { load(it) } }
    }

    /**
     * 进程内监视器避免重叠锁异常，文件锁使辅助进程的读取等待主进程提交完成。
     * @param file 缓存文件；同目录锁文件不随 AtomicFile 重命名。
     * @param action 持锁期间的完整事务，不在其中扫描 DEX 或更新界面。
     * @return 事务返回值；锁和句柄在异常时同样释放。
     * Callers: read、write。
     */
    @Synchronized private fun <T> access(file: File, action: (AtomicFile) -> T): T =
        RandomAccessFile(File(file.path + ".lock"), "rw").use { lock ->
            lock.channel.lock().use { action(AtomicFile(file)) }
        }
}
