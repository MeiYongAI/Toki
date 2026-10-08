package io.github.meiyongai.toki.hook

import io.github.libxposed.api.XposedModule
import io.github.meiyongai.toki.provider.ConfigClient

/** 解锁原生后台音频实验；播放启停及媒体会话仍由宿主管理。 */
object BackgroundAudioHook {
    const val KEY_ENABLED = "background_audio_unlock"

    /**
     * 放行后台音频实验，保留已启用的原生实验分组。
     * @param module 当前模块。
     * @param loader 宿主类加载器。
     * @return Unit。
     * Callers: TokiModule.installHost。
     */
    fun init(module: XposedModule, loader: ClassLoader) {
        val owner = HostSymbols.resolve(loader, HostSymbol.BACKGROUND_AUDIO)
        val group = owner.getDeclaredMethod(HostSymbols.member(HostSymbol.BACKGROUND_AUDIO, "group"))
        module.trackHook("BackgroundAudioHook", group).intercept { chain ->
            val original = chain.proceed() as Int
            if (ConfigClient.getBoolean(KEY_ENABLED) && original == 0) 2 else original
        }
    }
}
