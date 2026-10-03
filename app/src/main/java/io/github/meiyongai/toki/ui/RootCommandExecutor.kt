package io.github.meiyongai.toki.ui

import android.content.Context
import android.util.Log
import io.github.meiyongai.toki.hook.TokiModule
import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.TimeUnit
import io.github.meiyongai.toki.provider.HostRestartPolicy

/** 重启的明确执行结果；错误说明用于宿主扫描窗口。 */
internal data class HostRestartResult(val success: Boolean, val message: String)

/**
 * 超级用户（Root）终端指令执行器。
 *
 * 负责通过 su 命令在超级用户权限下停止并重新拉起目标宿主应用。
 */
object RootCommandExecutor {

    private const val TAG = "TokiRootExecutor"

    /**
     * 重启目标 TikTok 宿主应用进程。
     *
     * 自动检测当前设备中已安装的目标应用包名（优先匹配官方国际版与 Trill 渠道包），
     * 提取启动入口 Intent，通过超级用户（su）终端执行强制停止（am force-stop）并重新拉起启动入口（am start）。
     *
     * Args:
     *     context (Context): 界面上下文对象，用于检索已安装的应用及启动 Intent。
     *
     * Returns:
     *     Boolean: 重启命令成功执行返回 true；未检测到目标应用、未授予 Root 权限或执行异常返回 false。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.ui.MainActivity.MainAppScreen`: 点击顶部操作栏右侧重启图标时调度。
     */
    fun restartTikTok(context: Context): Boolean {
        val packageManager = context.packageManager
        val supportedPackages = listOf(TokiModule.PKG_TIKTOK_GLOBAL, TokiModule.PKG_TIKTOK_TRILL)

        val targetPackage = supportedPackages.firstOrNull { pkg ->
            packageManager.getLaunchIntentForPackage(pkg) != null
        }

        if (targetPackage == null) {
            Log.e(TAG, "未在当前设备检测到已安装的 TikTok 目标应用包")
            return false
        }

        return restartPackage(context, targetPackage).success
    }

    /**
     * 在 Toki 进程以已授予的 root 权限重启明确宿主；先验证启动组件，避免只停止却没有启动目标。
     * @param context Toki 上下文，不能传入宿主上下文。
     * @param targetPackage 已验证的受支持软件包。
     * @return 执行结果及明确错误原因；等待最多20秒。
     * Callers: restartTikTok。
     */
    internal fun restartPackage(context: Context, targetPackage: String): HostRestartResult {
        require(targetPackage in HostRestartPolicy.packages)
        val component = context.packageManager.getLaunchIntentForPackage(targetPackage)?.component
            ?: return HostRestartResult(false, "未找到 TikTok 启动入口，未执行停止操作。")
        // Android UserHandle.getUserId 的 UID 分组公式；getIdentifier 不是公开 SDK API。
        val userId = context.applicationInfo.uid / 100000
        val command = HostRestartPolicy.command(targetPackage, component.flattenToString(), userId)
        val process = try {
            ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
        } catch (e: IOException) {
            Log.e(TAG, "无法启动 su 进程，系统未提供 su 可执行文件或拒绝执行: ${e.message}", e)
            return HostRestartResult(false, "无法调用 root 服务，请确认已为 Toki 授权。")
        }

        val finished = try {
            process.waitFor(20, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Log.e(TAG, "等待 su 命令执行时发生线程中断: ${e.message}", e)
            Thread.currentThread().interrupt()
            process.destroy()
            return HostRestartResult(false, "重启任务被中断，请通过 Toki 手动重启 TikTok。")
        }
        if (!finished) {
            process.destroyForcibly()
            Log.e(TAG, "重启超时: $targetPackage")
            return HostRestartResult(false, "重启等待超时，请检查 Toki 的 root 授权后手动重启 TikTok。")
        }
        val output = try {
            process.inputStream.bufferedReader().use(BufferedReader::readText)
        } catch (e: IOException) {
            Log.e(TAG, "无法读取重启命令结果: $targetPackage", e)
            return HostRestartResult(false, "无法确认重启结果，请检查 TikTok 运行状态及 Toki 日志。")
        }
        val exitCode = process.exitValue()
        if (exitCode != 0 || output.lineSequence().any { it.startsWith("Error:") || it.startsWith("Exception") }) {
            Log.e(TAG, "su 命令执行失败，进程退出码: $exitCode, 终端输出: $output")
            return HostRestartResult(false, "自动重启失败（退出码 $exitCode），请确认已授予 Toki root 权限。")
        }

        Log.i(TAG, "su 命令成功执行: $command")
        return HostRestartResult(true, "重启命令已完成")
    }

    /**
     * 在超级用户（su）环境下执行命令并收集逐行文本输出。
     *
     * 启动系统 su 进程执行目标命令，读取其标准输出流并转换为字符串列表。
     * 若未授予 Root 权限或执行失败，则返回空列表并记录错误日志。
     *
     * Args:
     *     command (String): 需在超级用户权限下执行的 Shell 命令。
     *
     * Returns:
     *     List<String>: 命令执行产生的标准输出行列表；执行失败时返回空列表。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.ui.screen.LogReader.fetchLogs`: 获取系统全量 Toki 标签日志。
     */
    fun readSuCommandOutput(command: String): List<String> {
        val process = try {
            ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
        } catch (e: IOException) {
            Log.e(TAG, "无法启动 su 进程: ${e.message}", e)
            return emptyList()
        }

        val lines = mutableListOf<String>()
        try {
            process.inputStream.bufferedReader().useLines { sequence ->
                sequence.forEach { lines.add(it) }
            }
        } catch (e: IOException) {
            Log.e(TAG, "读取 su 输出流异常: ${e.message}", e)
            return emptyList()
        }

        try {
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                Log.w(TAG, "su 命令退出码非 0 ($exitCode): $command")
            }
        } catch (e: InterruptedException) {
            Log.e(TAG, "等待 su 进程结束时发生中断: ${e.message}", e)
            Thread.currentThread().interrupt()
            return emptyList()
        }

        return lines
    }
}
