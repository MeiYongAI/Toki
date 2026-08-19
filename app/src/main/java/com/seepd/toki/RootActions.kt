package com.seepd.toki

import android.content.Context
import java.io.IOException
import java.util.concurrent.TimeUnit

internal enum class RootActionStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    NO_ROOT,
    FAILED,
    TIMEOUT,
}

internal object RootActions {
    private const val ROOT_TIMEOUT_SECONDS = 45L
    private const val EXIT_NOT_ROOT = 10
    private const val EXIT_PACKAGE_MISSING = 11
    private const val EXIT_STOP_FAILED = 12
    private const val EXIT_START_FAILED = 13
    private const val EXIT_PROCESS_MISSING = 14
    private const val EXIT_CACHE_CLEAR_FAILED = 15

    /** Resolves the launcher in the app process, then performs only the privileged actions as root. */
    fun restartTikTok(context: Context): RootActionStatus {
        val target = findLaunchTarget(context) ?: return RootActionStatus.FAILED
        return restartTikTok(target, ProcessRootCommandExecutor)
    }

    fun clearTikTokCache(context: Context): RootActionStatus {
        if (!isTikTokInstalled(context)) return RootActionStatus.FAILED
        return clearTikTokCache(ProcessRootCommandExecutor)
    }

    private fun findLaunchTarget(context: Context): LaunchTarget? {
        val packageManager = context.packageManager
        val packageName = ModuleConfig.TARGET_PACKAGE
        val component = runCatching {
            packageManager.getLaunchIntentForPackage(packageName)?.component
        }.getOrNull() ?: return null
        return LaunchTarget(
            packageName = packageName,
            componentName = component.flattenToShortString(),
        )
    }

    internal fun restartTikTok(
        target: LaunchTarget?,
        executor: RootCommandExecutor,
    ): RootActionStatus {
        target ?: return RootActionStatus.FAILED
        return execute(buildRestartCommand(target), executor)
    }

    internal fun clearTikTokCache(executor: RootCommandExecutor): RootActionStatus =
        execute(buildClearCacheCommand(), executor)

    private fun execute(
        command: String,
        executor: RootCommandExecutor,
    ): RootActionStatus = when (val result = executor.execute(command, ROOT_TIMEOUT_SECONDS)) {
        RootCommandResult.Unavailable -> RootActionStatus.NO_ROOT
        RootCommandResult.Timeout -> RootActionStatus.TIMEOUT
        RootCommandResult.Interrupted -> RootActionStatus.FAILED
        is RootCommandResult.Completed -> when (result.exitCode) {
            0 -> RootActionStatus.SUCCESS
            1, 255, EXIT_NOT_ROOT -> RootActionStatus.NO_ROOT
            else -> RootActionStatus.FAILED
        }
    }

    internal fun buildRestartCommand(target: LaunchTarget): String {
        val packageName = shellQuote(target.packageName)
        val componentName = shellQuote(target.componentName)
        return "if [ \"$(/system/bin/id -u)\" != \"0\" ]; then exit $EXIT_NOT_ROOT; fi; " +
            "/system/bin/pm path --user current $packageName >/dev/null 2>&1 " +
            "|| exit $EXIT_PACKAGE_MISSING; " +
            "/system/bin/am force-stop --user current $packageName >/dev/null 2>&1 " +
            "|| exit $EXIT_STOP_FAILED; " +
            "/system/bin/am start -W --user current -n $componentName >/dev/null 2>&1 " +
            "|| exit $EXIT_START_FAILED; " +
            "/system/bin/pidof $packageName >/dev/null 2>&1 " +
            "|| exit $EXIT_PROCESS_MISSING"
    }

    internal fun buildClearCacheCommand(): String {
        val packageName = shellQuote(ModuleConfig.TARGET_PACKAGE)
        return "if [ \"$(/system/bin/id -u)\" != \"0\" ]; then exit $EXIT_NOT_ROOT; fi; " +
            "/system/bin/pm path --user current $packageName >/dev/null 2>&1 " +
            "|| exit $EXIT_PACKAGE_MISSING; " +
            "/system/bin/am force-stop --user current $packageName >/dev/null 2>&1 " +
            "|| exit $EXIT_STOP_FAILED; " +
            "/system/bin/pm clear --user current --cache-only $packageName >/dev/null 2>&1 " +
            "|| exit $EXIT_CACHE_CLEAR_FAILED"
    }

    private fun isTikTokInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(ModuleConfig.TARGET_PACKAGE, 0)
    }.isSuccess

    private fun shellQuote(value: String): String =
        "'${value.replace("'", "'\\''")}'"

    internal data class LaunchTarget(
        val packageName: String,
        val componentName: String,
    )
}

internal fun interface RootCommandExecutor {
    fun execute(command: String, timeoutSeconds: Long): RootCommandResult
}

internal sealed interface RootCommandResult {
    data class Completed(val exitCode: Int) : RootCommandResult

    data object Unavailable : RootCommandResult

    data object Timeout : RootCommandResult

    data object Interrupted : RootCommandResult
}

private object ProcessRootCommandExecutor : RootCommandExecutor {
    override fun execute(command: String, timeoutSeconds: Long): RootCommandResult {
        val process = try {
            ProcessBuilder("su", "-c", "{ $command; } >/dev/null 2>&1")
                .redirectErrorStream(true)
                .start()
        } catch (_: IOException) {
            return RootCommandResult.Unavailable
        } catch (_: SecurityException) {
            return RootCommandResult.Unavailable
        }

        return try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                RootCommandResult.Timeout
            } else {
                RootCommandResult.Completed(process.exitValue())
            }
        } catch (_: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
            RootCommandResult.Interrupted
        } finally {
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            runCatching { process.outputStream.close() }
        }
    }
}
