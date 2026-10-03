package io.github.meiyongai.toki.ui.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.MultiParagraph
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.meiyongai.toki.R
import io.github.meiyongai.toki.ui.screen.CustomGpsDialog
import io.github.meiyongai.toki.ui.screen.CustomLanguageDialog
import io.github.meiyongai.toki.ui.screen.CustomTimeZoneDialog
import io.github.meiyongai.toki.ui.screen.HookFeatureReport
import io.github.meiyongai.toki.ui.screen.HookProcessReport
import io.github.meiyongai.toki.ui.screen.HookStatusCatalog
import io.github.meiyongai.toki.ui.screen.HookStatusContent
import io.github.meiyongai.toki.util.AppLanguage
import io.github.meiyongai.toki.util.LocaleHelper
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import kotlin.math.ceil
import java.io.File

/** 使用真实Compose测量和Android字体在本地验证多语言控件，不启动Toki或TikTok。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class MultilingualLayoutTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var controller: ActivityController<ComponentActivity>
    private lateinit var composeView: ComposeView
    private var language by mutableStateOf(AppLanguage.ENGLISH)
    private var fontScale by mutableStateOf(1f)
    private val representativeTags = setOf("en", "de-DE", "ar", "zh-Hans")

    /**
     * 无描述的话题与创作者过滤开关在全部语言及放大字体下完整显示，并保持点击交互。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun recommendationSwitchesAreReadableAndInteractiveAcrossLanguages() {
        var checked by mutableStateOf(false)
        var changes = 0
        var titleResource by mutableStateOf(R.string.feature_filter_topics)
        setScene {
            SwitchPreferenceItem(
                title = LocalContext.current.getString(titleResource),
                checked = checked,
                onCheckedChange = { checked = it; changes++ },
                shape = getGroupedShape(0, 1)
            )
        }
        val screenshots = representativeTags + setOf("ur", "ja-JP")
        val cases = listOf(
            Triple(R.string.feature_filter_topics, "topics", "Hide suggested topics"),
            Triple(R.string.feature_filter_creators, "creators", "Hide suggested creators")
        )
        for ((resource, name, englishTitle) in cases) {
            compose.runOnIdle { titleResource = resource; checked = false; changes = 0 }
            for (selected in AppLanguage.entries.filter { it != AppLanguage.SYSTEM }) {
                for (scale in listOf(1f, 1.5f)) {
                    compose.runOnIdle { language = selected; fontScale = scale }
                    compose.waitForIdle()
                    if (selected.languageTag in screenshots) {
                        saveScreenshot("$name-${selected.languageTag}-$scale")
                    }
                    assertNoTextOverflow("$name/${selected.languageTag}/$scale")
                }
            }
            compose.runOnIdle { language = AppLanguage.ENGLISH; fontScale = 1f }
            compose.onNodeWithText(englishTitle).performClick()
            compose.runOnIdle {
                assertTrue(checked)
                assertEquals(1, changes)
            }
        }
    }

    /**
     * 建立仅供控件测试的Activity，不加载业务入口或设备连接。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Before fun createHost() {
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        composeView = ComposeView(controller.get())
        controller.get().setContentView(composeView)
    }

    /**
     * 先释放测试拥有的Composition和弹窗，再销毁Activity并执行已提交的主线程清理任务。
     * 保证Compose调度器完成清理，避免Robolectric重置消息队列后仍保留“已提交”的任务状态。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @After fun destroyHost() {
        compose.runOnUiThread { composeView.disposeComposition() }
        controller.pause().stop().destroy()
        shadowOf(Looper.getMainLooper()).idle()
    }

    /**
     * 全部语言在320dp窄屏、标准和放大字体下均完整测量，无文字被控件裁切。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun controlsKeepCompleteTextAcrossLanguagesAndFontScales() {
        setScene {
            val strings = LocalContext.current.resources
            CompactPreferenceSwitch(strings.getString(R.string.clean_on_play), true, {})
            CompactPreferenceSwitch(strings.getString(R.string.environment_follow_region), true, {})
            CompactPreferenceActions(strings.getString(R.string.region_select_preset), {},
                strings.getString(R.string.region_custom_parameters), {})
            CompactPreferenceActions(strings.getString(R.string.speed_add_option), {},
                strings.getString(R.string.speed_restore), {}, firstOutlined = true)
            CompactPreferenceCategories(listOf(R.string.keyword_description, R.string.keyword_tag, R.string.keyword_author)
                .map { strings.getString(R.string.keyword_category_count, strings.getString(it), 123) }, 0, {})
        }
        for (selected in AppLanguage.entries.filter { it != AppLanguage.SYSTEM }) {
            for (scale in listOf(1f, 1.5f)) {
                compose.runOnIdle { language = selected; fontScale = scale }
                compose.waitForIdle()
                assertNoTextOverflow("${selected.languageTag}/$scale")
                if (selected.languageTag in representativeTags) saveScreenshot("controls-${selected.languageTag}-$scale")
            }
        }
    }

    /**
     * 在窄屏和放大字体下验证状态页的长标题、状态及问题说明，覆盖左右两种阅读方向。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun statusRowsKeepTitlesAndStatesReadable() {
        val reports = HookStatusCatalog.features.mapIndexed { index, definition ->
            definition.id to when (index % 4) {
                0 -> HookFeatureReport(enabled = true, state = "已注册", registered = 1)
                1 -> HookFeatureReport(enabled = false)
                2 -> HookFeatureReport(enabled = true, state = "注册失败，已撤销")
                else -> HookFeatureReport(enabled = true)
            }
        }.toMap()
        val session = HookProcessReport("test.tiktok", 0L, reports)
        setScene(horizontalPadding = 0.dp) { HookStatusContent(listOf(session), null) }
        for (selected in AppLanguage.entries.filter { it.languageTag in representativeTags }) {
            for (scale in listOf(1f, 1.5f)) {
                compose.runOnIdle { language = selected; fontScale = scale }
                for (index in listOf(0, 6, 12, 17)) {
                    compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performScrollToIndex(index)
                    assertNoTextOverflow("status/${selected.languageTag}/$scale/$index")
                    if (index == 0) saveScreenshot("status-${selected.languageTag}-$scale")
                }
            }
        }
    }

    /**
     * 验证语言、时区与位置预设在窄弹窗内完整显示名称和标识，不将后者挤成窄列。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun presetDialogsKeepNamesAndValuesReadable() {
        var page by mutableStateOf(0)
        setScene {
            when (page) {
                0 -> CustomLanguageDialog("en-US", {}, {})
                1 -> CustomTimeZoneDialog("America/New_York", {}, {})
                else -> CustomGpsDialog("40.7128", "-74.0060", {}, { _, _ -> })
            }
        }
        for (selected in AppLanguage.entries.filter { it.languageTag in representativeTags }) {
            for (scale in listOf(1f, 1.5f)) {
                for (preset in 0..2) {
                    compose.runOnIdle { language = selected; fontScale = scale; page = preset }
                    compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performScrollToIndex(3)
                    assertNoTextOverflow("preset/$preset/${selected.languageTag}/$scale")
                }
            }
        }
    }

    /**
     * 文字尺寸自适应之后两个按钮仍各调用一次原始回调。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun compactActionsRetainClickCallbacks() {
        var firstClicks = 0
        var secondClicks = 0
        setScene {
            CompactPreferenceActions("A long primary action", { firstClicks++ },
                "A long secondary action", { secondClicks++ })
        }
        compose.onNodeWithText("A long primary action").performClick()
        compose.onNodeWithText("A long secondary action").performClick()
        compose.runOnIdle {
            assertEquals(1, firstClicks)
            assertEquals(1, secondClicks)
        }
    }

    /**
     * 验证布局断言确实识别被裁切的文字，不将测试接口的测量修正变成无效检查。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun overflowCheckRejectsClippedText() {
        setScene { Text("This text must not fit", Modifier.width(30.dp).height(10.dp), softWrap = false) }
        assertThrows(AssertionError::class.java) { assertNoTextOverflow("intentional clipping") }
    }

    /**
     * 在固定窄屏容器中提供当前语言及字体配置，以重复测量同一控件树。
     * @param horizontalPadding 模拟页面和配置卡片的总水平内边距；自带边距的页面传零。
     * @param content 不访问业务状态的待验证Compose控件。
     * @return Unit。
     * Callers: 本类布局和交互测试。
     */
    private fun setScene(horizontalPadding: Dp = 32.dp, content: @Composable () -> Unit) {
        composeView.setContent {
            val activity = controller.get()
            val context = LocaleHelper.wrapContext(activity, language, activity.resources.configuration)
            val direction = if (context.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL)
                LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides context.resources.configuration,
                LocalDensity provides Density(1f, fontScale),
                LocalLayoutDirection provides direction
            ) {
                MaterialTheme {
                    Surface {
                        Column(Modifier.width(320.dp).padding(horizontal = horizontalPadding, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            content()
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /**
     * 用Android View绘制接口保存本地Robolectric结果，检查文字密度、换行与从右向左布局。
     * 不依赖真实设备窗口的PixelCopy请求；所有绘制在测试主线程完成。
     * @param name 包含界面、语言和字体比例的文件名，不含扩展名。
     * @return Unit。
     * Callers: controlsKeepCompleteTextAcrossLanguagesAndFontScales、statusRowsKeepTitlesAndStatesReadable、
     * recommendationSwitchesAreReadableAndInteractiveAcrossLanguages。
     */
    private fun saveScreenshot(name: String) {
        val directory = File("build/reports/ui-polish")
        check(directory.isDirectory || directory.mkdirs())
        val bitmap = compose.runOnIdle {
            val view = controller.get().window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        File(directory, "$name.png").outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    /**
     * 按每个Text实际分配到的尺寸检测文字裁切，而非仅检查资源字符串长度。
     * Compose 1.7.5的String语义结果以父级最大宽度重建段落，段落空白宽度不等于控件宽度；
     * 因此沿用其字体和文本参数，按实际尺寸排版并校验完整行宽、行数及所需高度。
     * @param caseName 当前语言和字体缩放，用于测试失败诊断。
     * @return Unit。
     * Callers: controlsKeepCompleteTextAcrossLanguagesAndFontScales。
     */
    private fun assertNoTextOverflow(caseName: String) {
        val texts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
        val count = texts.fetchSemanticsNodes().size
        assertTrue("$caseName: no text nodes", count > 0)
        for (index in 0 until count) {
            val results = mutableListOf<TextLayoutResult>()
            texts[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            assertEquals(1, results.size)
            val result = results.single()
            val paragraph = MultiParagraph(
                intrinsics = result.multiParagraph.intrinsics,
                constraints = Constraints(maxWidth = if (result.layoutInput.softWrap) result.size.width
                    else ceil(result.multiParagraph.intrinsics.maxIntrinsicWidth).toInt()),
                maxLines = result.layoutInput.maxLines,
                ellipsis = false
            )
            val diagnostic = "$caseName: ${result.layoutInput.text}; size=${result.size}; " +
                "requiredHeight=${paragraph.height}; lines=${paragraph.lineCount}"
            assertFalse(diagnostic, paragraph.didExceedMaxLines)
            assertTrue(diagnostic, paragraph.height <= result.size.height)
            for (line in 0 until paragraph.lineCount) {
                val lineWidth = paragraph.getLineRight(line) - paragraph.getLineLeft(line)
                assertTrue("$diagnostic; lineWidth=$lineWidth", lineWidth <= result.size.width)
            }
        }
    }
}
