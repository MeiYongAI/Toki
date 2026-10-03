package io.github.meiyongai.toki.hook

import android.content.Context
import android.util.Log
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.libxposed.api.XposedModule

/**
 * 音频限制解锁 Hook（音乐版权状态纠正 + 视频静音解除）。
 *
 * 宿主音频播放与展示受两层服务端数据标记控制，其核心播放控制总门为
 * [com.ss.android.ugc.aweme.feed.controller.PlayerController.LLJL]：
 *
 * 1. 音乐可用状态（Music.musicStatus）：
 *    在宿主规范中，数值具有明确状态语义：
 *    - `0`：下架 / 无版权 / 地区封禁 / 移除（Offline，会导致播放控制器调用 LJFF 关闭音频，同时标记为 no_music 并隐藏唱片）；
 *    - `1`：正常在线 / 可用（Online，允许正常音频播放与展示）。
 *    在开启音乐解锁时，在上游数据模型 [com.ss.android.ugc.aweme.music.model.Music]
 *    与领域模型 [com.ss.android.ugc.aweme.shortvideo.model.MusicModel] 拦截状态读取，
 *    确保当数值为 0 时纠正为正常状态 1，同时清空离线说明文本（offlineDesc），并放行可用性判断（available）。
 *
 * 2. 视频静音标记（AwemeStatus$VideoMuteInfo）：
 *    当视频存在版权冲突或其他限制时，服务端下发 `is_mute = true` 与提示文本 `mute_desc`。
 *    在 [com.ss.android.ugc.aweme.feed.model.AwemeStatus.VideoMuteInfo] 上游拦截
 *    `isMute` 恒返回 false，`getMuteDesc` 恒返回空字符串，`getIsCopyrightViolation` 恒返回 false，
 *    阻止播放器执行静音分流及阻断警告弹窗展示。
 *
 * 开关状态实时通过 [ConfigClient] 读取，即时生效。
 */
object MusicUnlockHook {

    private const val TAG = "TokiMusicUnlock"

    /** 音频限制解锁配置键 */
    const val KEY_MUSIC_UNLOCK = "music_unlock"

    /** 音乐正常可用在线状态码 */
    private const val MUSIC_STATUS_ONLINE = 1

    /**
     * 读取音频限制解锁开关状态。
     *
     * Returns:
     *     Boolean: 开关是否启用。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.MusicUnlockHook.hookMusic`: 音乐模型拦截回调。
     *     - `io.github.meiyongai.toki.hook.MusicUnlockHook.hookMusicModel`: 表现层模型拦截回调。
     *     - `io.github.meiyongai.toki.hook.MusicUnlockHook.hookVideoMuteInfo`: 视频静音模型拦截回调。
     */
    private fun unlockEnabled(): Boolean = ConfigClient.getBoolean(KEY_MUSIC_UNLOCK)

    /**
     * 初始化挂载全部音频限制解锁 Hook，在上游数据模型层完成状态修正。
     *
     * Args:
     *     module (XposedModule): LSPosed 现代化模块注入上下文实例。
     *     classLoader (ClassLoader): 宿主目标应用的核心类加载器。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.TokiModule.onPackageLoaded`: 目标应用包完成加载时触发注册。
     */
    fun init(module: XposedModule, classLoader: ClassLoader) {
        hookMusic(module, classLoader)
        hookMusicModel(module, classLoader)
        hookVideoMuteInfo(module, classLoader)
        Log.i(TAG, "音频限制解锁 Hook 初始化挂载就绪")
    }

    /**
     * 目标应用 Application 上下文下的配置刷新同步入口。
     *
     * Args:
     *     context (Context): 目标应用上下文环境。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.TokiModule.hookApplication`: 宿主冷启动完成时分发。
     */
    fun refreshConfig(context: Context) {
        // 配置由 ConfigClient 统一管理并缓存
    }

    /**
     * 挂载 Music 上游数据模型状态拦截。
     *
     * 在音乐实体类拦截状态与描述：
     * 1. [getMusicStatus]：受限状态 0 纠正为在线状态 1，正常状态保持不变；
     * 2. [getOfflineDesc]：受限下线说明文案置空；
     * 3. [isPreventDownload]：下载限制放行；
     * 4. [available]：若包含有效播放地址且开启解锁则判定可用。
     *
     * Args:
     *     module (XposedModule): LSPosed 现代化模块注入上下文实例。
     *     classLoader (ClassLoader): 宿主目标应用的核心类加载器。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.MusicUnlockHook.init`: 模块初始化阶段挂载。
     */
    private fun hookMusic(module: XposedModule, classLoader: ClassLoader) {
        val musicClass = classLoader.loadClass("com.ss.android.ugc.aweme.music.model.Music")

        module.trackHook("MusicUnlockHook", musicClass.getMethod("getMusicStatus")).intercept { chain ->
            val result = chain.proceed()
            if (unlockEnabled() && result is Int && result == 0) {
                MUSIC_STATUS_ONLINE
            } else {
                result
            }
        }

        module.trackHook("MusicUnlockHook", musicClass.getMethod("getOfflineDesc")).intercept { chain ->
            if (unlockEnabled()) "" else chain.proceed()
        }

        module.trackHook("MusicUnlockHook", musicClass.getMethod("isPreventDownload")).intercept { chain ->
            if (unlockEnabled()) false else chain.proceed()
        }

        module.trackHook("MusicUnlockHook", musicClass.getMethod("available")).intercept { chain ->
            val result = chain.proceed()
            if (unlockEnabled() && result == false) {
                val thisObject = chain.thisObject
                val playUrl = musicClass.getMethod("getPlayUrl").invoke(thisObject)
                if (playUrl != null) true else false
            } else {
                result
            }
        }

        Log.i(TAG, "已挂载 Music 上游数据模型状态拦截")
    }

    /**
     * 挂载 MusicModel 表现层领域模型状态拦截。
     *
     * 确保拍摄、剪辑与音乐面板等下游消费模型与上游实体保持一致可用状态：
     * 1. [getMusicStatus]：受限状态 0 纠正为在线状态 1；
     * 2. [isOffline]：下线状态拦截返回 false；
     * 3. [getOfflineDesc]：下线文案置空。
     *
     * Args:
     *     module (XposedModule): LSPosed 现代化模块注入上下文实例。
     *     classLoader (ClassLoader): 宿主目标应用的核心类加载器。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.MusicUnlockHook.init`: 模块初始化阶段挂载。
     */
    private fun hookMusicModel(module: XposedModule, classLoader: ClassLoader) {
        val modelClass =
            classLoader.loadClass("com.ss.android.ugc.aweme.shortvideo.model.MusicModel")

        module.trackHook("MusicUnlockHook", modelClass.getMethod("getMusicStatus")).intercept { chain ->
            val result = chain.proceed()
            if (unlockEnabled() && result is Int && result == 0) {
                MUSIC_STATUS_ONLINE
            } else {
                result
            }
        }

        module.trackHook("MusicUnlockHook", modelClass.getMethod("isOffline")).intercept { chain ->
            if (unlockEnabled()) false else chain.proceed()
        }

        module.trackHook("MusicUnlockHook", modelClass.getMethod("getOfflineDesc")).intercept { chain ->
            if (unlockEnabled()) "" else chain.proceed()
        }

        Log.i(TAG, "已挂载 MusicModel 表现层领域模型状态拦截")
    }

    /**
     * 挂载 AwemeStatus$VideoMuteInfo 视频静音与版权违规标记拦截。
     *
     * 全面放行静音决策分支并清空提示弹窗：
     * 1. [isMute]：恒返回 false 放行音频输出；
     * 2. [getMuteDesc]：恒返回空字符串阻止错误弹窗；
     * 3. [getIsCopyrightViolation]：恒返回 false 解除版权违规判定。
     *
     * Args:
     *     module (XposedModule): LSPosed 现代化模块注入上下文实例。
     *     classLoader (ClassLoader): 宿主目标应用的核心类加载器。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.MusicUnlockHook.init`: 模块初始化阶段挂载。
     */
    private fun hookVideoMuteInfo(module: XposedModule, classLoader: ClassLoader) {
        val muteInfoClass =
            classLoader.loadClass("com.ss.android.ugc.aweme.feed.model.AwemeStatus\$VideoMuteInfo")

        module.trackHook("MusicUnlockHook", muteInfoClass.getMethod("isMute")).intercept { chain ->
            if (unlockEnabled()) false else chain.proceed()
        }

        module.trackHook("MusicUnlockHook", muteInfoClass.getMethod("getMuteDesc")).intercept { chain ->
            if (unlockEnabled()) "" else chain.proceed()
        }

        module.trackHook("MusicUnlockHook", muteInfoClass.getMethod(
            HostSymbols.member(HostSymbol.MUTE_INFO, "copyright")
        )).intercept { chain ->
            if (unlockEnabled()) false else chain.proceed()
        }

        Log.i(TAG, "已挂载 VideoMuteInfo 视频静音与版权标记放行拦截")
    }
}
