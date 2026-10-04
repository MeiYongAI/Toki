package io.github.meiyongai.toki.ui

import android.app.Application
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 使用应用 Manifest 验证桌面入口与 LSPosed 模块设置入口的独立性。 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28, 35])
class LauncherIconControllerTest {
    /**
     * 隐藏与恢复桌面图标时，LSPosed 均能解析到可导出的主活动。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test
    fun moduleSettingsRemainAvailableAcrossIconChanges() {
        val context = RuntimeEnvironment.getApplication()
        val settingsIntent = Intent(Intent.ACTION_MAIN)
            .addCategory("de.robv.android.xposed.category.MODULE_SETTINGS")
            .setPackage(context.packageName)

        for (hidden in listOf(false, true, false)) {
            LauncherIconController.setIconHidden(context, hidden)
            assertEquals(hidden, LauncherIconController.isIconHidden(context))
            val matches = context.packageManager.queryIntentActivities(settingsIntent, 0)
            assertEquals("LSPosed must resolve one settings entry (hidden=$hidden)", 1, matches.size)
            val activity = matches.single().activityInfo
            assertEquals(MainActivity::class.java.name, activity.name)
            assertTrue(activity.enabled)
            assertTrue(activity.exported)
        }
    }

    /**
     * 桌面只显示启动别名，隐藏后没有桌面入口，恢复后仍只有一个入口。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test
    fun launcherEntryTracksIconVisibility() {
        val context = RuntimeEnvironment.getApplication()
        val launcherIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(context.packageName)

        assertFalse(LauncherIconController.isIconHidden(context))
        for (hidden in listOf(false, true, false)) {
            LauncherIconController.setIconHidden(context, hidden)
            val matches = context.packageManager.queryIntentActivities(launcherIntent, 0)
            assertEquals(if (hidden) 0 else 1, matches.size)
            if (!hidden) {
                assertEquals("io.github.meiyongai.toki.LauncherAlias", matches.single().activityInfo.name)
                assertEquals(MainActivity::class.java.name, matches.single().activityInfo.targetActivity)
            }
        }
    }
}
