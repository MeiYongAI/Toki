package io.github.meiyongai.toki.hook

import android.graphics.Rect
import android.widget.ImageView
import java.lang.reflect.Modifier
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 图文沉浸策略：完整图片在实际视口内等比居中，文案作为覆盖层显示。
 * @param dispatcher 行为规则唯一定位的宿主图文布局分发类。
 * @param name 分发方法名。
 * Callers: ImmersiveFullScreenHook.init；ImmersivePhotoLayoutTest。
 */
internal class ImmersivePhotoLayout(dispatcher: Class<*>, name: String) {
    val calculate = dispatcher.declaredMethods.filter { it.name == name }.single().apply {
        check(Modifier.isStatic(modifiers) && parameterCount == 8 &&
            parameterTypes[0].name == "com.ss.android.ugc.aweme.ui.layout.FeedContentLayoutInfo" &&
            parameterTypes.slice(1..6).all { it == Int::class.javaPrimitiveType } &&
            !parameterTypes[7].isPrimitive && !returnType.isPrimitive) { "图文布局分发签名不符合契约" }
        isAccessible = true
    }
    private val resultConstructor = calculate.returnType.getDeclaredConstructor(
        ImageView.ScaleType::class.java, Float::class.javaPrimitiveType,
        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Rect::class.java,
        Float::class.javaPrimitiveType, Rect::class.java, Float::class.javaPrimitiveType,
        Float::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
        String::class.java, calculate.returnType, Map::class.java
    ).apply { isAccessible = true }

    /**
     * 构造宿主图文布局结果；调用方仅在图片和视口完成有效测量后使用。
     * @param imageWidth 原图宽度（像素）。
     * @param imageHeight 原图高度（像素）。
     * @param width 图文视口宽度（像素）。
     * @param height 图文视口高度（像素）。
     * @return 宿主布局对象；高度比例为 1，图片等比完整显示且无额外顶部边距或位移。
     * Callers: ImmersiveFullScreenHook.init 注册的图文策略 Hook；ImmersivePhotoLayoutTest。
     */
    fun centered(imageWidth: Int, imageHeight: Int, width: Int, height: Int): Any {
        require(imageWidth > 0 && imageHeight > 0 && width > 0 && height > 0)
        val scale = min(width.toDouble() / imageWidth, height.toDouble() / imageHeight)
        val contentWidth = (imageWidth * scale).roundToInt()
        val contentHeight = (imageHeight * scale).roundToInt()
        val left = (width - contentWidth) / 2
        val top = (height - contentHeight) / 2
        val content = Rect(left, top, left + contentWidth, top + contentHeight)
        return resultConstructor.newInstance(ImageView.ScaleType.FIT_CENTER, 1f, height, height,
            content, 1f, null, 0f, 0f, 0, 0, "", null, null)
    }
}
