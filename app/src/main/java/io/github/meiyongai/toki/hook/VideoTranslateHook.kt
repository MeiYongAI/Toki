package io.github.meiyongai.toki.hook

import io.github.meiyongai.toki.provider.ConfigClient
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Modifier

/** 解锁长按菜单中的“字幕和翻译”入口。 */
object VideoTranslateHook {
    const val KEY_VIDEO_TRANSLATE_ENABLED = "video_translate_enabled"

    /**
     * 注册字幕菜单访问控制拦截；字幕与翻译行为由宿主管理。
     * @param module 当前 LSPosed 模块。
     * @param classLoader 宿主类加载器。
     * @return Unit；契约不完整时由统一注册事务报告失败。
     * Callers: TokiModule.installHost。
     */
    fun init(module: XposedModule, classLoader: ClassLoader) {
        val translationGate = HostSymbols.resolve(classLoader, HostSymbol.TRANSLATION_REVERSE)
            .getDeclaredMethod(HostSymbols.member(HostSymbol.TRANSLATION_REVERSE, "blocked"))
            .apply {
                check(Modifier.isStatic(modifiers) && returnType == Boolean::class.javaPrimitiveType)
                isAccessible = true
            }
        module.trackHook("VideoTranslateHook", translationGate).intercept { chain ->
            val blocked = chain.proceed() as Boolean
            blocked && !ConfigClient.getBoolean(KEY_VIDEO_TRANSLATE_ENABLED)
        }
        val captionConsumer = HostSymbols.resolve(classLoader, HostSymbol.CAPTION_CONSUMER)
        val captionGate = captionConsumer.declaredMethods.single {
            Modifier.isStatic(it.modifiers) && it.parameterCount == 0 && it.returnType == Boolean::class.javaPrimitiveType
        }.apply { isAccessible = true }
        module.trackHook("VideoTranslateHook", captionGate).intercept { chain ->
            if (ConfigClient.getBoolean(KEY_VIDEO_TRANSLATE_ENABLED)) true else chain.proceed()
        }
        val menuAcl = HostSymbols.resolve(classLoader, HostSymbol.MENU_CAPTION_ACL).getDeclaredMethod(
            HostSymbols.member(HostSymbol.MENU_CAPTION_ACL, "resolve"),
            String::class.java, String::class.java,
            classLoader.loadClass("com.ss.android.ugc.aweme.feed.model.AwemeACLShare")
        )
        module.trackHook("VideoTranslateHook", menuAcl).intercept { chain ->
            if (ConfigClient.getBoolean(KEY_VIDEO_TRANSLATE_ENABLED) &&
                chain.args[0] == "captions" && chain.args[1] == "long_press") null
            else chain.proceed()
        }
    }
}
