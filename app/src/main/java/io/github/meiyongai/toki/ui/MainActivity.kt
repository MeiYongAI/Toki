package io.github.meiyongai.toki.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex
import io.github.meiyongai.toki.R
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.meiyongai.toki.ui.screen.DashboardScreen
import io.github.meiyongai.toki.ui.screen.HomeScreen
import io.github.meiyongai.toki.ui.screen.HookStatusSection
import io.github.meiyongai.toki.ui.screen.LanguageScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 模块桌面管理端主界面。
 *
 * 遵循原生 Android 设置设计语言，通过 [enableEdgeToEdge] 与 Material 3 DayNight 自适应配色，
 * 采用原生标准尺寸顶部标题栏（[TopAppBar]），负责展示与修改模块配置，并通过 [ConfigClient] 与私有数据源同步。
 */
class MainActivity : ComponentActivity() {
    /**
     * 活动组件生命周期创建入口。
     *
     * 开启沉浸式无边框布局并绑定自适应主题与双页面主视图树。
     *
     * @param savedInstanceState 界面重建时保存的状态包。
     * @return Unit。
     *
     * Callers:
     * - `android.app.ActivityThread.performLaunchActivity`: 系统启动桌面组件的标准流程。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        ConfigClient.init(applicationContext)
        io.github.meiyongai.toki.util.LSPosedStatusHelper.init()
        setContent {
            LocalizedTokiApp {
                TokiAppTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainAppScreen()
                    }
                }
            }
        }
    }
}

/**
 * 应用全局主题包装器。
 *
 * 根据系统深浅色模式与 Android 12+ 动态壁纸颜色（Dynamic Color）自动构建 Material 3 配色方案。
 *
 * @param content 子可组合项树。
 * @return Unit。
 *
 * Callers:
 * - `io.github.meiyongai.toki.ui.MainActivity.onCreate`: 注入全局主题外观。
 */
@Composable
fun TokiAppTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current

    val colorScheme = tokiColorScheme(context, darkTheme)

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

/** 共用导航过渡的首页次级页面，不保存与功能开关有关的数据。 */
private enum class SecondaryPage { HOOK_STATUS, LANGUAGE }

/**
 * 模块主骨架全景视图（MainAppScreen）。
 *
 * 采用 Material 3 双页面设计架构，顶部单行标题栏（[TopAppBar]），底部原生导航栏（[NavigationBar]）。
 * 首页与配置页保留独立滚动状态。次级页类型与显隐状态分别保存，状态页和语言页使用同一
 * 显隐过渡驱动返回按钮、底栏占位与页面内容。关闭时保留页面内容直到退出动画完成；
 * 底栏测量高度连续变化，避免 Scaffold 内容区域突然跳变。
 *
 * Args:
 *     无。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.MainActivity.onCreate`: 活动主入口渲染树根节点。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    val strings = context.resources
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var dashboardLoaded by rememberSaveable { mutableStateOf(false) }
    var secondaryPage by rememberSaveable { mutableStateOf(SecondaryPage.HOOK_STATUS) }
    var showSecondaryPage by rememberSaveable { mutableStateOf(false) }
    val statusTransition = updateTransition(showSecondaryPage, label = "SecondaryPageNavigation")
    val layoutDirection = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    BackHandler(enabled = showSecondaryPage) { showSecondaryPage = false }

    // 冷启动性能优化：在首帧绘制及启动退出动画完成后，于主线程空闲时间后台静默预热功能配置页树
    LaunchedEffect(Unit) {
        delay(350L)
        dashboardLoaded = true
    }

    val homeScrollState = rememberScrollState()
    val dashboardScrollState = rememberScrollState()

    // 页面双向视差切换动画驱动：进入 250ms，退出 220ms，缓动采用 FastOutSlowInEasing
    val homeFraction = remember { Animatable(1f) }
    val dashboardFraction = remember { Animatable(0f) }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 0) {
            launch {
                homeFraction.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
                )
            }
            launch {
                dashboardFraction.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                )
            }
        } else {
            launch {
                dashboardFraction.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
                )
            }
            launch {
                homeFraction.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = if (showSecondaryPage) secondaryPage.ordinal + 2 else selectedTab,
                        transitionSpec = {
                            val direction = (if (targetState > initialState) 1 else -1) * layoutDirection
                            (slideInHorizontally(
                                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
                            ) { fullWidth -> (fullWidth * direction) / 5 } + fadeIn(
                                animationSpec = tween(durationMillis = 250, easing = LinearEasing)
                            )).togetherWith(
                                slideOutHorizontally(
                                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                                ) { fullWidth -> (-direction * fullWidth) / 5 } + fadeOut(
                                    animationSpec = tween(durationMillis = 220, easing = LinearEasing)
                                )
                            )
                        },
                        label = "SettingsPageTransition"
                    ) { pageIndex ->
                        val title = when (pageIndex) {
                            0 -> "Toki"
                            2 -> strings.getString(R.string.page_hook_status)
                            3 -> strings.getString(R.string.language_title)
                            else -> strings.getString(R.string.page_features)
                        }
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                navigationIcon = {
                    statusTransition.AnimatedVisibility(
                        visible = { it },
                        enter = expandHorizontally(tween(300, easing = FastOutSlowInEasing), expandFrom = Alignment.Start) +
                            fadeIn(tween(220, delayMillis = 60)),
                        exit = shrinkHorizontally(tween(300, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Start) +
                            fadeOut(tween(180))
                    ) {
                        IconButton(enabled = showSecondaryPage, onClick = { showSecondaryPage = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.getString(R.string.nav_back))
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                val success = withContext(Dispatchers.IO) {
                                    RootCommandExecutor.restartTikTok(context)
                                }
                                if (!success) {
                                    Toast.makeText(
                                        context,
                                        strings.getString(R.string.restart_root_required),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_top_refresh),
                            contentDescription = strings.getString(R.string.restart_tiktok)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().windowInsetsPadding(NavigationBarDefaults.windowInsets)) {
                statusTransition.AnimatedVisibility(
                    visible = { !it },
                    enter = expandVertically(tween(300, easing = FastOutSlowInEasing), expandFrom = Alignment.Bottom) +
                        fadeIn(tween(220, delayMillis = 60)),
                    exit = shrinkVertically(tween(300, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Bottom) +
                        fadeOut(tween(180))
                ) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.background,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        tonalElevation = 0.dp
                    ) {
                        NavigationBarItem(
                            enabled = !showSecondaryPage,
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == 0) Icons.Filled.Home else Icons.Outlined.Home,
                                    contentDescription = strings.getString(R.string.nav_home)
                                )
                            },
                            label = { Text(strings.getString(R.string.nav_home)) }
                        )
                        NavigationBarItem(
                            enabled = !showSecondaryPage,
                            selected = selectedTab == 1,
                            onClick = {
                                dashboardLoaded = true
                                selectedTab = 1
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == 1) Icons.Filled.Tune else Icons.Outlined.Tune,
                                    contentDescription = strings.getString(R.string.nav_features)
                                )
                            },
                            label = { Text(strings.getString(R.string.nav_features)) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 首页视图：常驻挂载；当前 Tab 为首页或处于退场视差滑动动画中时渲染，完全滑出后移出视口
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (selectedTab == 0) 1f else 0f)
                    .graphicsLayer {
                        alpha = homeFraction.value
                        val width = size.width
                        translationX = if (selectedTab == 0 || homeFraction.value > 0f) {
                            (homeFraction.value - 1f) * (width / 5f) * layoutDirection
                        } else {
                            -10000f * layoutDirection
                        }
                    }
            ) {
                HomeScreen(
                    scrollState = homeScrollState,
                    onHookStatusClick = {
                        secondaryPage = SecondaryPage.HOOK_STATUS
                        showSecondaryPage = true
                    },
                    onLanguageClick = {
                        secondaryPage = SecondaryPage.LANGUAGE
                        showSecondaryPage = true
                    }
                )
            }

            // 功能页视图：冷启动后后台静默预热或首次点击时同步挂载；与 Re.X 官方动效保持一致的 20% 视差滑入
            if (dashboardLoaded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(if (selectedTab == 1) 1f else 0f)
                        .graphicsLayer {
                            alpha = dashboardFraction.value
                            val width = size.width
                            translationX = if (selectedTab == 1 || dashboardFraction.value > 0f) {
                                (1f - dashboardFraction.value) * (width / 5f) * layoutDirection
                            } else {
                                10000f * layoutDirection
                            }
                        }
                ) {
                    val importedRevision by io.github.meiyongai.toki.provider.ConfigClient.importedRevision.collectAsState()
                    androidx.compose.runtime.key(importedRevision) {
                        DashboardScreen(scrollState = dashboardScrollState)
                    }
                }
            }
            statusTransition.AnimatedVisibility(
                visible = { it },
                modifier = Modifier.fillMaxSize().zIndex(2f),
                enter = slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { layoutDirection * it / 5 } + fadeIn(tween(300)),
                exit = slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { layoutDirection * it / 5 } + fadeOut(tween(250))
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (secondaryPage) {
                        SecondaryPage.HOOK_STATUS -> HookStatusSection()
                        SecondaryPage.LANGUAGE -> LanguageScreen()
                    }
                }
            }
        }
    }
}
