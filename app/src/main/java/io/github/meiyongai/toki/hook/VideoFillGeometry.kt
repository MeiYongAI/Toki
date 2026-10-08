package io.github.meiyongai.toki.hook

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 按实际视口决定有限裁切或完整等比显示，不以固定视频分辨率判断。 */
internal object VideoFillGeometry {
    /** 渲染尺寸与缩放依据；中心位置由宿主居中容器承担。 */
    data class Size(val width: Int, val height: Int, val cropped: Boolean, val fitHeight: Boolean)

    /**
     * 仅为竖屏视口中、裁切面积不超过四分之一的竖屏视频选择铺满。
     * @param videoWidth 视频原始宽度。
     * @param videoHeight 视频原始高度。
     * @param width 视口宽度。
     * @param height 视口高度。
     * @return 等比例显示尺寸；未测量或无效尺寸返回 null，不改变宿主结果。
     * Callers: SmartVideoLayout.replace；VideoFillGeometryTest。
     */
    fun calculate(videoWidth: Int, videoHeight: Int, width: Int, height: Int): Size? {
        if (minOf(videoWidth, videoHeight, width, height) <= 0) return null
        val x = width.toDouble() / videoWidth
        val y = height.toDouble() / videoHeight
        val cover = videoHeight > videoWidth && height > width && min(x, y) / max(x, y) >= 0.75
        val scale = if (cover) max(x, y) else min(x, y)
        val w = videoWidth * scale
        val h = videoHeight * scale
        // 铺满向上取整，避免像素舍入产生一像素留边；完整显示取最近整数。
        return Size(if (cover) ceil(w).toInt() else w.roundToInt().coerceAtLeast(1),
            if (cover) ceil(h).toInt() else h.roundToInt().coerceAtLeast(1), cover,
            if (cover) y >= x else y <= x)
    }
}
