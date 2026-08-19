package com.seepd.toki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootActionsTest {
    private val target = RootActions.LaunchTarget(
        packageName = "com.zhiliaoapp.musically",
        componentName = "com.zhiliaoapp.musically/.MainActivity",
    )

    @Test
    fun successfulRootCommandIsReported() {
        var timeout = 0L
        val executor = RootCommandExecutor { _, timeoutSeconds ->
            timeout = timeoutSeconds
            RootCommandResult.Completed(0)
        }

        assertEquals(RootActionStatus.SUCCESS, RootActions.restartTikTok(target, executor))
        assertEquals(45L, timeout)
    }

    @Test
    fun unavailableAndDeniedRootAreReported() {
        val unavailable = RootCommandExecutor { _, _ -> RootCommandResult.Unavailable }
        val denied = RootCommandExecutor { _, _ -> RootCommandResult.Completed(255) }
        val notRoot = RootCommandExecutor { _, _ -> RootCommandResult.Completed(10) }

        assertEquals(RootActionStatus.NO_ROOT, RootActions.restartTikTok(target, unavailable))
        assertEquals(RootActionStatus.NO_ROOT, RootActions.restartTikTok(target, denied))
        assertEquals(RootActionStatus.NO_ROOT, RootActions.restartTikTok(target, notRoot))
    }

    @Test
    fun timeoutAndCommandFailureRemainDistinct() {
        val timeout = RootCommandExecutor { _, _ -> RootCommandResult.Timeout }
        val failure = RootCommandExecutor { _, _ -> RootCommandResult.Completed(13) }

        assertEquals(RootActionStatus.TIMEOUT, RootActions.restartTikTok(target, timeout))
        assertEquals(RootActionStatus.FAILED, RootActions.restartTikTok(target, failure))
    }

    @Test
    fun missingLaunchTargetDoesNotRequestRoot() {
        var executed = false
        val executor = RootCommandExecutor { _, _ ->
            executed = true
            RootCommandResult.Completed(0)
        }

        assertEquals(RootActionStatus.FAILED, RootActions.restartTikTok(null, executor))
        assertFalse(executed)
    }

    @Test
    fun commandRestartsOnlyResolvedPackageAndVerifiesLaunch() {
        val command = RootActions.buildRestartCommand(target)

        assertTrue(command.contains("am force-stop --user current 'com.zhiliaoapp.musically'"))
        assertTrue(command.contains("am start -W --user current -n 'com.zhiliaoapp.musically/.MainActivity'"))
        assertTrue(command.contains("pidof 'com.zhiliaoapp.musically'"))
    }

    @Test
    fun cacheCommandClearsOnlyTikTokCache() {
        val command = RootActions.buildClearCacheCommand()

        assertTrue(command.contains("am force-stop --user current 'com.zhiliaoapp.musically'"))
        assertTrue(
            command.contains(
                "pm clear --user current --cache-only 'com.zhiliaoapp.musically'",
            ),
        )
        assertFalse(command.contains("pm clear --user current 'com.zhiliaoapp.musically'"))
    }

    @Test
    fun cacheClearReportsRootAndCommandResults() {
        val success = RootCommandExecutor { _, _ -> RootCommandResult.Completed(0) }
        val denied = RootCommandExecutor { _, _ -> RootCommandResult.Completed(255) }
        val failure = RootCommandExecutor { _, _ -> RootCommandResult.Completed(15) }

        assertEquals(RootActionStatus.SUCCESS, RootActions.clearTikTokCache(success))
        assertEquals(RootActionStatus.NO_ROOT, RootActions.clearTikTokCache(denied))
        assertEquals(RootActionStatus.FAILED, RootActions.clearTikTokCache(failure))
    }
}
