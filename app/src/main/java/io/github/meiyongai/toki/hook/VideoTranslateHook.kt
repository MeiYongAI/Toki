package io.github.meiyongai.toki.hook

import android.util.Log
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Modifier

/** 解除信息流翻译停用实验与正文标记限制；语言、内容类型和翻译状态由宿主判断。 */
object VideoTranslateHook {
    const val KEY_VIDEO_TRANSLATE_ENABLED = "video_translate_enabled"

    /**
     * 校验正文与实验入口，解除停用实验，在正文资格调用期间放行同一视频的标记。
     * @param module 当前 LSPosed 模块。
     * @param classLoader 宿主类加载器。
     * @return Unit；契约不完整时由统一注册事务报告失败。
     * Callers: TokiModule.installHost。
     */
    fun init(module: XposedModule, classLoader: ClassLoader) {
        val aweme = classLoader.loadClass("com.ss.android.ugc.aweme.feed.model.Aweme")
        val service = HostSymbols.resolve(classLoader, HostSymbol.DESCRIPTION_TRANSLATION)
        val eligible = service.getDeclaredMethod(
            HostSymbols.member(HostSymbol.DESCRIPTION_TRANSLATION, "eligible"), aweme
        ).apply { isAccessible = true }
        val flag = aweme.getDeclaredMethod("isDescTranslatable").apply { isAccessible = true }
        val desc = aweme.getDeclaredMethod("getDesc").apply { isAccessible = true }
        val experiment = HostSymbols.resolve(classLoader, HostSymbol.TRANSLATION_REVERSE)
            .getDeclaredMethod(HostSymbols.member(HostSymbol.TRANSLATION_REVERSE, "blocked"))
            .apply { isAccessible = true }
        check(listOf(eligible, flag).all { it.returnType == Boolean::class.javaPrimitiveType } &&
            desc.returnType == String::class.java &&
            experiment.returnType == Boolean::class.javaPrimitiveType && Modifier.isStatic(experiment.modifiers) &&
            listOf(eligible, flag, desc).none { Modifier.isStatic(it.modifiers) }) {
            "正文翻译入口或模型类型不符合契约"
        }
        val scope = DescriptionTranslationScope()
        // 保留宿主缓存的真实实验值，每次调用按开关返回；关闭后恢复原生结果。
        module.trackHook("VideoTranslateHook", experiment).intercept { chain ->
            val blocked = chain.proceed() as Boolean
            blocked && !ConfigClient.getBoolean(KEY_VIDEO_TRANSLATE_ENABLED)
        }
        module.trackHook("VideoTranslateHook", flag).intercept { chain ->
            val original = chain.proceed() as Boolean
            original || scope.allows(chain.thisObject) &&
                !(desc.invoke(chain.thisObject) as String?).isNullOrBlank()
        }
        module.trackHook("VideoTranslateHook", eligible).intercept { chain ->
            if (ConfigClient.getBoolean(KEY_VIDEO_TRANSLATE_ENABLED)) {
                scope.checking(chain.args[0]) { chain.proceed() }
            } else chain.proceed()
        }
        Log.i("TokiVideoTrans", "正文翻译入口已注册：${service.name}.${eligible.name}；实验门控：${experiment.declaringClass.name}.${experiment.name}")
    }
}

/** 将正文标记放行限定于同步资格检查，不修改模型或其他翻译流程。 */
internal class DescriptionTranslationScope {
    private val current = ThreadLocal<Any?>()

    /**
     * 执行原生资格判断，并在正常返回或异常时恢复外层上下文。
     * @param video 当前正文所属的视频。
     * @param block 原生资格判断。
     * @return 原生判断结果。
     * Callers: VideoTranslateHook.init 的资格拦截、DescriptionTranslationScopeTest。
     */
    fun <T> checking(video: Any?, block: () -> T): T {
        val previous = current.get()
        current.set(video)
        return try { block() } finally {
            if (previous == null) current.remove() else current.set(previous)
        }
    }

    /**
     * 判断模型读取是否属于当前正文资格检查。
     * @param video 模型读取的接收对象。
     * @return 仅当前线程的同一非空对象返回 true。
     * Callers: VideoTranslateHook.init 的模型拦截、DescriptionTranslationScopeTest。
     */
    fun allows(video: Any?): Boolean = video != null && current.get() === video
}
