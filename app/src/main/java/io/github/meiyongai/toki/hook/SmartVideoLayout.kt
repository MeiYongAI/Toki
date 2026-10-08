package io.github.meiyongai.toki.hook

import java.lang.reflect.Modifier

/**
 * 在信息流布局提交入口替换视频尺寸，保留宿主参数和结果类型。
 * @param feed 已验证的信息流适配组件。
 * @param result 视频适配结果类。
 * @param operator 多容器适配结果类型。
 * Callers: ImmersiveFullScreenHook.init；SmartVideoLayoutTest。
 */
internal class SmartVideoLayout(feed: Class<*>, result: Class<*>, private val operator: Class<*>) {
    var lastGeometry: String = "等待视频适配"
        private set
    val apply = feed.declaredMethods.single {
        !Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE &&
            it.parameterTypes.contentEquals(arrayOf(result, String::class.java))
    }.apply { isAccessible = true }
    private val getOperator = result.getMethod("getResultOperator")
    private val resultConstructor = result.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
        java.lang.Float::class.java, java.lang.Float::class.java, getOperator.returnType)
    private val getParams = operator.getMethod("getVideoAdaptionParams")
    private val dimensions = listOf("getVideoWidth", "getVideoHeight", "getContainerWidth", "getContainerHeight")
        .map { name -> getParams.returnType.getMethod(name).apply {
            check(returnType == Int::class.javaPrimitiveType && !Modifier.isStatic(modifiers))
        } }
    private val align = operator.getMethod("getAlignType").returnType
    private val scale = operator.getMethod("getAdaptionScaleType").returnType
    private val heightAlign = align.getField("HEIGHT")
    private val widthAlign = align.getField("WIDTH")
    private val cropScale = scale.getField("CROP")
    private val fitScale = scale.getField("FIT")
    private val getters = listOf("getTopType", "getBottomType", "getTopHeight", "getBottomHeight",
        "getAdjustContainerHeight", "getAdjustContainerWidth", "getAdaptionScaleType", "getAlignType",
        "getAreaDiff", "getVideoAdaptionParams", "getOcrEffective").map { operator.getMethod(it) }
    private val operatorConstructor = operator.getConstructor(*getters.map { it.returnType }.toTypedArray())

    init {
        check(getOperator.returnType.isAssignableFrom(operator))
        check(align.isEnum && scale.isEnum)
        for (field in listOf(heightAlign, widthAlign, cropScale, fitScale)) check(Modifier.isStatic(field.modifiers))
        // 验证父类消费同一契约；读取类型信息不初始化宿主枚举或静态对象。
        check(feed.superclass.getDeclaredMethod(apply.name, result, String::class.java).returnType == Void.TYPE)
    }

    /**
     * 对支持的宿主策略构造居中等比结果，未测量及其他策略沿用原结果。
     * @param original 宿主当前计算结果，可以为空。
     * @return 新结果或原对象；不原地修改宿主缓存中的结果。
     * Callers: ImmersiveFullScreenHook 的视频提交拦截；SmartVideoLayoutTest。
     */
    fun replace(original: Any?): Any? {
        if (original == null) return null
        val source = getOperator.invoke(original)
        if (!operator.isInstance(source)) {
            lastGeometry = "宿主特殊适配策略，保留原结果"
            return original
        }
        val params = getParams.invoke(source)
        val d = dimensions.map { it.invoke(params) as Int }
        val size = VideoFillGeometry.calculate(d[0], d[1], d[2], d[3]) ?: return original
        lastGeometry = "video=${d[0]}x${d[1]} viewport=${d[2]}x${d[3]} result=${size.width}x${size.height} crop=${size.cropped}"
        val args = getters.map { it.invoke(source) }.toTypedArray()
        args[2] = 0f
        args[3] = 0f
        args[4] = d[3].toFloat()
        args[5] = d[2].toFloat()
        args[6] = (if (size.cropped) cropScale else fitScale).get(null)
        args[7] = (if (size.fitHeight) heightAlign else widthAlign).get(null)
        val adjusted = operatorConstructor.newInstance(*args)
        return resultConstructor.newInstance(size.width, size.height, 0f, 0f, adjusted)
    }
}
