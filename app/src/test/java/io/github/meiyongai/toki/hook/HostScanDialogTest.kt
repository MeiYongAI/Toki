package io.github.meiyongai.toki.hook

import android.app.Application
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import io.github.meiyongai.toki.util.AppLanguage
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowProcess
import java.io.File
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import java.util.Properties

/** 用真实模块资源、独立 Dialog 生命周期和 Compose 测量验证窄屏方法查找窗口。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "b+zh+Hans-w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class HostScanDialogTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    private var dialog: HostScanDialog? = null
    private var scanLifecycle: Application.ActivityLifecycleCallbacks? = null

    /** 使用 320dp 屏幕与放大字体，窗口仍按宿主配置加载模块 APK 资源。 */
    @Before fun createHost() {
        RuntimeEnvironment.setFontScale(1.5f)
        ShadowProcess.setPid(4703)
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
    }

    /** Dialog 自身的 Lifecycle 负责释放 Composition，清理后再销毁宿主活动。 */
    @After fun destroyHost() {
        compose.runOnUiThread { dialog?.dismiss() }
        shadowOf(Looper.getMainLooper()).idle()
        controller.pause().stop().destroy()
        shadowOf(Looper.getMainLooper()).idle()
        scanLifecycle?.let { controller.get().application.unregisterActivityLifecycleCallbacks(it) }
        scanLifecycle?.let { controllerField().set(null, null) }
    }

    /** 准备阶段不播放可能闪满的循环动画，得到工作量后直接显示真实进度。 */
    @Test fun preparationStaysEmptyUntilMeasuredProgressArrives() {
        val session = HostScanSession()
        session.begin()
        show(session.status) {}
        val indicator = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        indicator.assertRangeInfoEquals(ProgressBarRangeInfo(0f, 0f..1f))
        compose.onNodeWithText("0%").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1500)
        indicator.assertRangeInfoEquals(ProgressBarRangeInfo(0f, 0f..1f))
        compose.runOnIdle {
            session.progress(0, 4)
            dialog!!.render(session.status)
        }
        indicator.assertRangeInfoEquals(ProgressBarRangeInfo(0f, 0f..1f))
        compose.runOnIdle {
            session.progress(1, 4)
            dialog!!.render(session.status)
        }
        indicator.assertRangeInfoEquals(ProgressBarRangeInfo(0.25f, 0f..1f))
        compose.onNodeWithText("25%").assertIsDisplayed()
    }

    /** 普通字号缩小窗口，同时保留说明、进度和完成操作的可见空间。 */
    @Test fun defaultFontUsesCompactWindow() {
        compose.runOnUiThread {
            controller.pause().stop().destroy()
            RuntimeEnvironment.setFontScale(1f)
            controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        }
        show(HostScanStatus(HostScanPhase.SCANNING, 1, 2), fontScale = 1f) {}
        compose.onNodeWithText("正在查找方法").assertIsDisplayed()
        compose.onNodeWithText("保持 TikTok 在前台。").assertIsDisplayed()
        compose.onNodeWithText("50%").assertIsDisplayed()
        saveScreenshot("running-320dp-1.0")
        compose.runOnIdle { dialog!!.render(HostScanStatus(HostScanPhase.READY, 2, 2)) }
        compose.onNodeWithText("请手动重启 TikTok 以应用结果。").assertIsDisplayed()
        compose.onNodeWithText("知道了").assertIsDisplayed()
        saveScreenshot("completed-320dp-1.0")
    }

    /** 真实工作量显示 50%，类名不泄露到 UI；完成按钮只确认结果并关闭窗口。 */
    @Test fun progressStaysCompactAndCompletionButtonLeavesHostRunning() {
        val session = HostScanSession()
        var acknowledgements = 0
        session.begin()
        session.progress(1, 2)
        show(session.status) {
            acknowledgements++
            session.dismissResult()
            dialog!!.dismiss()
        }

        compose.onNodeWithText("50%").assertIsDisplayed()
        compose.onNodeWithText("知道了").assertDoesNotExist()
        saveScreenshot("running-320dp-1.5")
        val window = dialog!!.window!!
        val initialWidth = window.attributes.width
        val initialHeight = window.attributes.height

        compose.runOnIdle {
            session.progress(2, 2)
            session.saving()
            session.ready()
            dialog!!.render(session.status)
        }
        compose.onNodeWithText("查找完成").assertIsDisplayed()
        compose.onNodeWithText("请手动重启 TikTok 以应用结果。").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("知道了").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(initialWidth, window.attributes.width)
            assertEquals(initialHeight, window.attributes.height)
        }
        saveScreenshot("completed-320dp-1.5")

        compose.onNodeWithText("知道了").performClick()
        compose.runOnIdle {
            assertEquals(1, acknowledgements)
            assertEquals(HostScanPhase.IDLE, session.status.phase)
            assertFalse(dialog!!.isShowing)
            assertFalse(controller.get().isFinishing)
            assertFalse(controller.get().isDestroyed)
            assertFalse(ShadowProcess.wasKilled(4703))
        }
    }

    /** 失败仅引导查看功能诊断；关闭按钮同样不销毁宿主或终止进程。 */
    @Test fun failureButtonDismissesOnlyResultDialog() {
        val session = HostScanSession()
        var acknowledgements = 0
        session.begin()
        session.fail()
        show(session.status) {
            acknowledgements++
            session.dismissResult()
            dialog!!.dismiss()
        }
        compose.onNodeWithText("请在 Toki 查看功能诊断。").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("关闭").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, acknowledgements)
            assertEquals(HostScanPhase.IDLE, session.status.phase)
            assertFalse(dialog!!.isShowing)
            assertFalse(controller.get().isFinishing)
            assertFalse(controller.get().isDestroyed)
            assertFalse(ShadowProcess.wasKilled(4703))
        }
    }

    /** 未确认结果在暂停后恢复；确认后再恢复不会重新显示，也不会结束宿主。 */
    @Test fun controllerRestoresPendingCompletionButKeepsAcknowledgedResultDismissed() {
        compose.runOnUiThread {
            controller.pause()
            HostScanController.session.fail()
            HostScanController.session.dismissResult()
            controllerField().set(null, null)
            HostScanController.attach(controller.get().application, moduleResources())
            scanLifecycle = controllerField().get(null) as Application.ActivityLifecycleCallbacks
            HostScanController.session.apply {
                begin()
                progress(1, 1)
                saving()
                ready()
            }
            controller.resume()
        }
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
        compose.onNodeWithText("知道了").assertIsDisplayed()
        val first = currentControllerDialog()!!

        compose.runOnUiThread { controller.pause() }
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(first.isShowing)
        assertNull(currentControllerDialog())
        assertEquals(HostScanPhase.READY, HostScanController.session.status.phase)

        compose.runOnUiThread { controller.resume() }
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
        val restored = currentControllerDialog()!!
        assertNotSame(first, restored)
        compose.onNodeWithText("知道了").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertFalse(restored.isShowing)
            assertNull(currentControllerDialog())
            assertEquals(HostScanPhase.IDLE, HostScanController.session.status.phase)
        }

        compose.runOnUiThread { controller.pause().resume() }
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(currentControllerDialog())
        assertEquals(HostScanPhase.IDLE, HostScanController.session.status.phase)
        assertFalse(controller.get().isFinishing)
        assertFalse(controller.get().isDestroyed)
        assertFalse(ShadowProcess.wasKilled(4703))
    }

    /** 宿主语言决定真实窗口文案、数字和 RTL 排版；长句可滚动而结果操作一直可达。 */
    @Test fun localizedDialogsKeepProgressAndResultActionsUsableInNarrowLargeFontWindow() {
        for (language in listOf(AppLanguage.ENGLISH, AppLanguage.DE_DE, AppLanguage.AR)) {
            val tag = language.languageTag
            compose.runOnUiThread {
                dialog?.dismiss()
                controller.pause().stop().destroy()
                RuntimeEnvironment.setQualifiers("b+${tag.replace('-', '+')}-w320dp-h640dp-mdpi")
                RuntimeEnvironment.setFontScale(1.5f)
                controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
            }
            val expected = hostScanTranslations(language)
            val session = HostScanSession()
            var acknowledgements = 0
            session.begin()
            session.progress(1, 3)
            show(session.status) {
                acknowledgements++
                session.dismissResult()
                dialog!!.dismiss()
            }
            val window = dialog!!.window!!
            val initialWidth = window.attributes.width
            val initialHeight = window.attributes.height
            val percent = NumberFormat.getPercentInstance(Locale.forLanguageTag(tag)).apply {
                maximumFractionDigits = 0
                roundingMode = RoundingMode.FLOOR
            }.format(1.0 / 3.0)
            compose.onNodeWithText(expected.getValue("host_scan_title_running")).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(expected.getValue("host_scan_keep_foreground")).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(percent).assertIsDisplayed()
            assertNoChineseText(tag)
            if (language == AppLanguage.AR) {
                compose.runOnIdle {
                    assertEquals(View.LAYOUT_DIRECTION_RTL, dialog!!.context.resources.configuration.layoutDirection)
                }
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(expected.getValue("host_scan_title_running"))
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals(LayoutDirection.Rtl, layouts.single().layoutInput.layoutDirection)
            }
            saveScreenshot("running-$tag-320dp-1.5")

            compose.runOnIdle {
                session.progress(3, 3)
                session.saving()
                dialog!!.render(session.status)
            }
            compose.onNodeWithText(expected.getValue("host_scan_title_saving")).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(expected.getValue("host_scan_saving_hint")).performScrollTo().assertIsDisplayed()
            compose.runOnIdle {
                session.ready()
                dialog!!.render(session.status)
            }
            compose.onNodeWithText(expected.getValue("host_scan_title_complete")).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(expected.getValue("host_scan_restart_hint")).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(expected.getValue("host_scan_done")).assertIsDisplayed()
            val button = compose.onNodeWithText(expected.getValue("host_scan_done")).fetchSemanticsNode().boundsInRoot
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            if (language == AppLanguage.AR) {
                assertTrue("$tag: confirmation must align to the logical end (left)", button.center.x < root.center.x)
            } else {
                assertTrue("$tag: confirmation must align to the logical end (right)", button.center.x > root.center.x)
            }
            assertNoChineseText(tag)
            compose.runOnIdle {
                assertEquals(initialWidth, window.attributes.width)
                assertEquals(initialHeight, window.attributes.height)
            }
            saveScreenshot("completed-$tag-320dp-1.5")
            compose.onNodeWithText(expected.getValue("host_scan_done")).performClick()
            compose.runOnIdle {
                assertEquals(1, acknowledgements)
                assertFalse(dialog!!.isShowing)
                assertFalse(controller.get().isFinishing)
                assertFalse(controller.get().isDestroyed)
                assertFalse(ShadowProcess.wasKilled(4703))
            }

            session.begin()
            session.fail()
            show(session.status) {
                acknowledgements++
                session.dismissResult()
                dialog!!.dismiss()
            }
            compose.onNodeWithText(expected.getValue("host_scan_title_failed")).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(expected.getValue("host_scan_failure_hint")).performScrollTo().assertIsDisplayed()
            assertNoChineseText(tag)
            compose.onNodeWithText(expected.getValue("common_close")).assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(2, acknowledgements) }
        }
    }

    /** 遍历实际语义树，防止未显示的中文状态文案仍残留在非中文窗口。 */
    private fun assertNoChineseText(caseName: String) {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue("$caseName: no window text", nodes.isNotEmpty())
        val visibleText = nodes.flatMap { it.config[SemanticsProperties.Text] }.joinToString(" ") { it.text }
        assertFalse("$caseName: Chinese text leaked into translated window: $visibleText",
            Regex("[\\u3400-\\u9fff]").containsMatchIn(visibleText))
    }

    /** 使用反射只读取窗口与隔离静态注册，实际显示和确认仍经过生产 Controller。 */
    private fun controllerField() = HostScanController::class.java.getDeclaredField("controller").apply {
        isAccessible = true
    }

    private fun currentControllerDialog(): HostScanDialog? =
        HostScanController::class.java.getDeclaredField("dialog").apply { isAccessible = true }
            .get(scanLifecycle) as HostScanDialog?

    /** 直接使用编译后的模块 APK，避免在测试中复制 Composable 或替代其资源上下文。 */
    private fun moduleResources(): ModuleResources {
        val configuration = Properties().apply {
            HostScanDialogTest::class.java.classLoader!!
                .getResourceAsStream("com/android/tools/test_config.properties")!!.use(::load)
        }
        val apk = File(configuration.getProperty("android_resource_apk")).absoluteFile
        assertTrue("Compiled module resource APK is missing: $apk", apk.isFile)
        val info = ApplicationInfo(controller.get().applicationInfo).apply {
            sourceDir = apk.path
            publicSourceDir = apk.path
            uid += 1
        }
        return ModuleResources(info, javaClass.classLoader!!)
    }

    private fun show(status: HostScanStatus, fontScale: Float = 1.5f, onResultDismiss: () -> Unit) {
        compose.runOnUiThread {
            dialog = HostScanDialog(controller.get(), moduleResources(), onResultDismiss)
                .also { it.render(status); it.show() }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(dialog!!.isShowing)
            assertEquals(fontScale, dialog!!.context.resources.configuration.fontScale, 0f)
            val density = dialog!!.context.resources.displayMetrics.density
            assertEquals((272 * density).toInt(), dialog!!.window!!.attributes.width)
            assertEquals(((if (fontScale == 1f) 200 else 240) * density).toInt(), dialog!!.window!!.attributes.height)
        }
    }

    /** 绘制真实 Dialog 的 decorView，保留扫描中与完成时的本地布局截图。 */
    private fun saveScreenshot(name: String) {
        val directory = File("build/reports/host-scan-dialog")
        check(directory.isDirectory || directory.mkdirs())
        val bitmap = compose.runOnIdle {
            val view = dialog!!.window!!.decorView
            assertTrue("Dialog must be measured before drawing", view.width > 0 && view.height > 0)
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        try {
            File(directory, "$name.png").outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
    }
}
