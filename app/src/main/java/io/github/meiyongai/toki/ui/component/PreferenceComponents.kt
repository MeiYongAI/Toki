package io.github.meiyongai.toki.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 分组条目的自适应圆角形状，记录静态几何四角半径以支持按压动态形变。
 *
 * 实现了 [Shape] 接口，可直接作为 Compose 的通用 Shape 使用。
 * 内部记录 [topStart]、[topEnd]、[bottomStart]、[bottomEnd] 规格，
 * 供 [MorphingPreferenceContainer] 读取并在手势交互时执行 Spring 弹性形变过渡。
 *
 * Args:
 *     topStart (Dp): 左上角圆角半径。
 *     topEnd (Dp): 右上角圆角半径。
 *     bottomStart (Dp): 左下角圆角半径。
 *     bottomEnd (Dp): 右下角圆角半径。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.component.PreferenceComponents.getGroupedShape`: 创建位置自适应形状。
 */
class GroupCornerShape(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomStart: Dp,
    val bottomEnd: Dp
) : Shape {
    private val roundedCornerShape = RoundedCornerShape(
        topStart = topStart,
        topEnd = topEnd,
        bottomEnd = bottomEnd,
        bottomStart = bottomStart
    )

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        return roundedCornerShape.createOutline(size, layoutDirection, density)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GroupCornerShape) return false
        return topStart == other.topStart &&
                topEnd == other.topEnd &&
                bottomStart == other.bottomStart &&
                bottomEnd == other.bottomEnd
    }

    override fun hashCode(): Int {
        var result = topStart.hashCode()
        result = 31 * result + topEnd.hashCode()
        result = 31 * result + bottomStart.hashCode()
        result = 31 * result + bottomEnd.hashCode()
        return result
    }
}

/**
 * 根据条目在分组列表中的位置，计算自适应圆角形状。
 *
 * 遵循 Material 3 与 Re.X 分组卡片交互规范：
 * - 当分组内仅有单个元素时，四角均为大圆角；
 * - 列表首项：顶部大圆角，底部微圆角；
 * - 列表中间项：四角均为微圆角；
 * - 列表尾项：顶部微圆角，底部大圆角。
 *
 * 返回兼具 [Shape] 接口与四角度量的 [GroupCornerShape]，
 * 支持在 [MorphingPreferenceContainer] 中平滑进行形变计算。
 *
 * Args:
 *     index (int): 当前条目在分组内的索引（从 0 开始）。
 *     count (int): 当前分组所包含的条目总数。
 *     cornerRadius (Dp): 大圆角半径，默认 20.dp。
 *     middleRadius (Dp): 列表中间接缝微圆角半径，默认 4.dp。
 *
 * Returns:
 *     GroupCornerShape: 对应位置的圆角几何形状。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.HomeScreen`: 首页各条目的自适应形状计算。
 *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 功能页各卡片及条目的自适应形状计算。
 */
fun getGroupedShape(
    index: Int,
    count: Int,
    cornerRadius: Dp = 20.dp,
    middleRadius: Dp = 4.dp
): GroupCornerShape {
    if (count <= 1) {
        return GroupCornerShape(cornerRadius, cornerRadius, cornerRadius, cornerRadius)
    }
    return when (index) {
        0 -> GroupCornerShape(
            topStart = cornerRadius,
            topEnd = cornerRadius,
            bottomStart = middleRadius,
            bottomEnd = middleRadius
        )
        count - 1 -> GroupCornerShape(
            topStart = middleRadius,
            topEnd = middleRadius,
            bottomStart = cornerRadius,
            bottomEnd = cornerRadius
        )
        else -> GroupCornerShape(
            topStart = middleRadius,
            topEnd = middleRadius,
            bottomStart = middleRadius,
            bottomEnd = middleRadius
        )
    }
}

/**
 * 具有受约束涟漪动画与长按/按压弹性形变反馈的通用交互容器。
 *
 * 实现了 Material 3 与 Re.X 交互反馈：
 * 1. 严格将按压水波纹涟漪（Ripple）限定在当前自适应动态圆角轮廓内部，杜绝矩形直角溢出；
 * 2. 监听用户按压（Press）手势，驱动四角平滑过渡到 20.dp 全圆角胶囊形态的 Spring 弹性形变动画；
 * 3. 伴随 0.985f 的轻微下沉压感缩放；
 * 4. 支持长按并触发系统级触觉震动反馈（HapticFeedback）。
 *
 * Args:
 *     shape (Shape): 原始未按压状态下的外形轮廓（若为 [GroupCornerShape] 则自动提取四角执行形态演进）。
 *     modifier (Modifier): 外层修饰符。
 *     containerColor (Color): 容器背景色，默认为 surfaceContainer。
 *     enabled (Boolean): 是否响应点击交互。
 *     onClick (() -> Unit)?): 单击事件回调。
 *     onLongClick (() -> Unit)?): 长按事件回调。
 *     content (@Composable BoxScope.() -> Unit): 容器内部子布局插槽。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.component.PreferenceItem`: 构建标准偏好项。
 *     - `io.github.meiyongai.toki.ui.component.SwitchPreferenceItem`: 构建开关偏好项。
 *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 具备点击与长按交互的设置卡片。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MorphingPreferenceContainer(
    shape: Shape,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current

    val springSpec = spring<Dp>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val targetMorphRadius = 20.dp
    val baseCorners = (shape as? GroupCornerShape) ?: GroupCornerShape(20.dp, 20.dp, 20.dp, 20.dp)

    val animatedTopStart by animateDpAsState(
        targetValue = if (isPressed) targetMorphRadius else baseCorners.topStart,
        animationSpec = springSpec,
        label = "TopStartMorph"
    )
    val animatedTopEnd by animateDpAsState(
        targetValue = if (isPressed) targetMorphRadius else baseCorners.topEnd,
        animationSpec = springSpec,
        label = "TopEndMorph"
    )
    val animatedBottomStart by animateDpAsState(
        targetValue = if (isPressed) targetMorphRadius else baseCorners.bottomStart,
        animationSpec = springSpec,
        label = "BottomStartMorph"
    )
    val animatedBottomEnd by animateDpAsState(
        targetValue = if (isPressed) targetMorphRadius else baseCorners.bottomEnd,
        animationSpec = springSpec,
        label = "BottomEndMorph"
    )

    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "ScaleMorph"
    )

    val animatedContainerColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isPressed) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            containerColor
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "ColorMorph"
    )

    val currentShape = RoundedCornerShape(
        topStart = animatedTopStart,
        topEnd = animatedTopEnd,
        bottomEnd = animatedBottomEnd,
        bottomStart = animatedBottomStart
    )

    val clickableModifier = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = ripple(color = MaterialTheme.colorScheme.primary),
            enabled = enabled,
            onClick = { onClick?.invoke() },
            onLongClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongClick?.invoke()
            }
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .clip(currentShape)
            .background(color = animatedContainerColor, shape = currentShape)
            .then(clickableModifier),
        content = content
    )
}

/**
 * 紧凑型功能分组区块容器。
 *
 * 渲染一个带分类标头与微间距条目列的分组卡片块，
 * 条目之间以极窄间距（2.dp）排列，配合自适应圆角营造一体化 Material 3 视觉卡片体验。
 *
 * Args:
 *     title (String): 分组模块的分类标题名称。
 *     modifier (Modifier): 容器外层修饰符。
 *     content (ColumnScope.() -> Unit): 分组内部容纳的条目插槽内容。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.HomeScreen`: 首页各分类设置项。
 *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 承载四大业务分类模块。
 */
@Composable
fun PreferenceSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, bottom = 6.dp, top = 4.dp)
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content
        )
    }
}

/**
 * 标准紧凑型设置交互项（支持点击、长按反馈、动态形变与受约束涟漪）。
 *
 * Args:
 *     title (String): 设置项主标题。
 *     summary (String?): 设置项副标题或当前状态说明文本。
 *     shape (Shape): 设置项的自适应圆角几何形状。
 *     modifier (Modifier): 修饰符。
 *     icon (ImageVector?): 左侧图标，可选。
 *     iconTint (Color): 左侧图标色调，默认为主题主色。
 *     onClick (() -> Unit)?): 点击回调事件。
 *     onLongClick (() -> Unit)?): 长按回调事件（可选）。
 *     trailingContent (@Composable () -> Unit)?): 右侧自定义组件（如数值徽标或右箭头）。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.HomeScreen`: 首页各设置与状态条目。
 *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 触发数值配置对话框的设置条目。
 */
@Composable
fun PreferenceItem(
    title: String,
    summary: String? = null,
    shape: Shape,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    MorphingPreferenceContainer(
        shape = shape,
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
            }
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                if (!summary.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(12.dp))
                trailingContent()
            } else if (onClick != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * 紧凑型开关设置项（支持整行点击切换、长按反馈、动态形变与受约束涟漪）。
 *
 * Args:
 *     title (String): 开关项主标题。
 *     summary (String?): 开关项详细描述文字。
 *     checked (Boolean): 开关当前开启状态。
 *     onCheckedChange ((Boolean) -> Unit): 开关状态切换回调。
 *     shape (Shape): 设置项的自适应圆角几何形状。
 *     modifier (Modifier): 修饰符。
 *     icon (ImageVector?): 左侧功能图标。
 *     iconTint (Color): 左侧图标颜色。
 *     onLongClick (() -> Unit)?): 长按回调事件（可选）。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.HomeScreen`: 首页开关设置项。
 *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 功能页各功能模块开关项。
 */
@Composable
fun SwitchPreferenceItem(
    title: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onLongClick: (() -> Unit)? = null
) {
    MorphingPreferenceContainer(
        shape = shape,
        modifier = modifier,
        onClick = { onCheckedChange(!checked) },
        onLongClick = onLongClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) iconTint else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
            }
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                if (!summary.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
