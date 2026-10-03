package io.github.meiyongai.toki.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * 在紧凑配置卡片中先保留开关的标准尺寸，再由标题和摘要使用剩余宽度。
 *
 * 文本允许自然换行，不限制行数；保留配置页的字号、字重和摘要间距。
 *
 * @param title 功能或子功能的完整标题。
 * @param checked 当前开关状态。
 * @param onCheckedChange 开关状态变化回调，不处理配置读写。
 * @param modifier 配置行的布局修饰符。
 * @param summary 可选的完整摘要，为 null 时不占用摘要空间。
 * @return Unit。
 * Callers: DashboardScreen 的地区、语言、时区、定位、倍速、清屏、路径、过滤和时长卡片。
 */
@Composable
internal fun CompactPreferenceSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            if (summary != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * 按按钮的实际文本宽度和 Material 3 内边距决定双按钮并排或纵向排列。
 *
 * 并排时先满足各按钮的文字宽度，再平分剩余空间；纵向时使用完整可用宽度并允许换行。
 * 不缩小字体，不省略文字，按钮的点击行为由调用方提供。
 *
 * @param firstLabel 第一个按钮的完整文案。
 * @param onFirstClick 第一个按钮的点击回调。
 * @param secondLabel 第二个按钮的完整文案。
 * @param onSecondClick 第二个按钮的点击回调。
 * @param modifier 按钮组的布局修饰符。
 * @param firstOutlined 第一个按钮是否使用描边样式；第二个按钮固定使用描边样式。
 * @param spacing 按钮之间的水平或垂直间距。
 * @return Unit。
 * Callers: DashboardScreen.SimSpoofCard、DashboardScreen.PlaybackSpeedCard。
 */
@Composable
internal fun CompactPreferenceActions(
    firstLabel: String,
    onFirstClick: () -> Unit,
    secondLabel: String,
    onSecondClick: () -> Unit,
    modifier: Modifier = Modifier,
    firstOutlined: Boolean = false,
    spacing: Dp = 8.dp
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.labelLarge
    val padding = ButtonDefaults.ContentPadding
    val horizontalPadding = padding.calculateLeftPadding(layoutDirection) +
        padding.calculateRightPadding(layoutDirection)
    val textWidths = listOf(firstLabel, secondLabel).map { label ->
        val width = textMeasurer.measure(label, style = textStyle, softWrap = false).size.width
        with(density) { width.toDp() }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widths = calculateCompactActionWidths(
            textWidths[0], textWidths[1], maxWidth, horizontalPadding, ButtonDefaults.MinWidth, spacing
        )
        if (widths != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                CompactActionButton(
                    firstLabel, onFirstClick, firstOutlined,
                    Modifier.width(widths.first)
                )
                CompactActionButton(
                    secondLabel, onSecondClick, true,
                    Modifier.width(widths.second)
                )
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
                CompactActionButton(firstLabel, onFirstClick, firstOutlined, Modifier.fillMaxWidth())
                CompactActionButton(secondLabel, onSecondClick, true, Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * 根据文本实测宽度分配两个并排按钮的宽度，空间不足时明确要求纵向排列。
 * @param firstTextWidth 第一个按钮文字的实测宽度，不含内边距。
 * @param secondTextWidth 第二个按钮文字的实测宽度，不含内边距。
 * @param availableWidth 按钮组可用宽度。
 * @param horizontalPadding 单个按钮左右内边距之和。
 * @param minimumButtonWidth Material 3 按钮的最小宽度。
 * @param spacing 两个按钮之间的间距。
 * @return 并排时两个按钮的宽度；无法完整并排时为 null。
 * Callers: CompactPreferenceActions、配置控件尺寸测试。
 */
internal fun calculateCompactActionWidths(
    firstTextWidth: Dp,
    secondTextWidth: Dp,
    availableWidth: Dp,
    horizontalPadding: Dp,
    minimumButtonWidth: Dp,
    spacing: Dp
): Pair<Dp, Dp>? {
    val firstMinimum = (firstTextWidth + horizontalPadding).coerceAtLeast(minimumButtonWidth)
    val secondMinimum = (secondTextWidth + horizontalPadding).coerceAtLeast(minimumButtonWidth)
    val requiredWidth = firstMinimum + secondMinimum + spacing
    if (requiredWidth > availableWidth) return null
    val extraWidth = (availableWidth - requiredWidth) / 2
    return firstMinimum + extraWidth to secondMinimum + extraWidth
}

/**
 * 按指定按钮类型绘制允许完整换行的居中文案。
 * @param label 按钮的完整文案。
 * @param onClick 按钮点击回调。
 * @param outlined 是否使用 Material 3 描边按钮。
 * @param modifier 由按钮组计算的尺寸修饰符。
 * @return Unit。
 * Callers: CompactPreferenceActions。
 */
@Composable
private fun CompactActionButton(label: String, onClick: () -> Unit, outlined: Boolean, modifier: Modifier) {
    if (outlined) {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(label, textAlign = TextAlign.Center)
        }
    } else {
        Button(onClick = onClick, modifier = modifier) {
            Text(label, textAlign = TextAlign.Center)
        }
    }
}

/**
 * 绘制按文本尺寸扩展的圆角分类栏，以滑动背景指示当前分类。
 *
 * 同时测量普通和选中字重，确保切换后文字仍完整显示。宽度不足时仅分类栏可横向滚动；
 * 外部容器保持父布局的宽度约束，不扩展弹窗。短标签仍使用等宽布局和至少 32dp 的行高。
 *
 * @param labels 非空的完整分类名称列表，可包含规则数量。
 * @param selectedIndex 当前分类索引，必须属于 labels.indices。
 * @param onSelectedIndexChange 用户点击分类时的回调，不处理分类业务状态。
 * @param modifier 分类栏的布局修饰符。
 * @return Unit。
 * Callers: DashboardScreen.KeywordListDialog。
 */
@Composable
internal fun CompactPreferenceCategories(
    labels: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    require(labels.isNotEmpty()) { "分类列表不能为空" }
    require(selectedIndex in labels.indices) { "当前分类索引必须属于分类列表" }
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.labelMedium
    val textSizes = labels.flatMap { label ->
        listOf(FontWeight.Normal, FontWeight.SemiBold).map { weight ->
            textMeasurer.measure(label, style = textStyle.copy(fontWeight = weight), softWrap = false).size
        }
    }
    val maximumTextWidth = with(density) { textSizes.maxOf { it.width }.toDp() }
    val maximumTextHeight = with(density) { textSizes.maxOf { it.height }.toDp() }
    val scrollState = rememberScrollState()
    BoxWithConstraints(
        modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(3.dp)
    ) {
        val itemSize = calculateCompactCategorySize(maximumTextWidth, maximumTextHeight, maxWidth, labels.size)
        val itemWidth = itemSize.width
        val itemHeight = itemSize.height
        val indicatorOffset by animateDpAsState(
            targetValue = itemWidth * selectedIndex,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "tabIndicatorOffset"
        )
        Box(Modifier.horizontalScroll(scrollState)) {
            Box(Modifier.width(itemWidth * labels.size).height(itemHeight)) {
                Box(
                    Modifier.offset(x = indicatorOffset).width(itemWidth).height(itemHeight)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                )
                Row(Modifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
                    labels.forEachIndexed { index, label ->
                        val selected = index == selectedIndex
                        val textColor by animateColorAsState(
                            targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "tabTextColor"
                        )
                        Box(
                            modifier = Modifier.width(itemWidth).height(itemHeight)
                                .clip(RoundedCornerShape(8.dp))
                                .selectable(
                                    selected = selected,
                                    role = Role.Tab,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onSelectedIndexChange(index) }
                                ).padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = textStyle,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = textColor,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 计算分类项的共同尺寸，使普通和选中状态的文字均拥有完整显示空间。
 * @param maximumTextWidth 所有标签在两种字重下测得的最大文本宽度。
 * @param maximumTextHeight 所有标签在两种字重下测得的最大文本高度。
 * @param availableWidth 已扣除分类栏外部内边距的可用宽度。
 * @param categoryCount 分类数量，必须大于零。
 * @return 包含文本内边距且满足紧凑控件最小高度的共同分类项尺寸。
 * Callers: CompactPreferenceCategories、配置控件尺寸测试。
 */
internal fun calculateCompactCategorySize(
    maximumTextWidth: Dp,
    maximumTextHeight: Dp,
    availableWidth: Dp,
    categoryCount: Int
): DpSize {
    require(categoryCount > 0) { "分类数量必须大于零" }
    return DpSize(
        width = (availableWidth / categoryCount).coerceAtLeast(maximumTextWidth + 16.dp),
        height = (maximumTextHeight + 8.dp).coerceAtLeast(32.dp)
    )
}
