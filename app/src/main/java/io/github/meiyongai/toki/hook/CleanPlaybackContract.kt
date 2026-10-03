package io.github.meiyongai.toki.hook

/** 将明确的暂停交互关联到其实际列表与播放器，不推断播放请求返回码。 */
internal class CleanPlaybackContract(controller: Class<*>, listPanel: Class<*>, aweme: Class<*>) {
    val panel = controller.declaredMethods.single { it.parameterCount == 0 && it.returnType == listPanel }
    val handle = controller.declaredMethods.single {
        it.returnType == Void.TYPE && it.parameterTypes.contentEquals(arrayOf(aweme,
            Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType))
    }
    val pause = controller.getMethod("pauseVideo")
    private val manager = controller.getMethod("getPlayerManager")
    private val paused = manager.returnType.getMethod("isPaused")

    /**
     * 读取明确交互执行后的实际暂停结果。
     * @param instance 接收交互的控制器。
     * @return 是否已经暂停。
     * Callers: AutoCleanModeHook.playbackStopped。
     */
    fun isPaused(instance: Any): Boolean = paused.invoke(manager.invoke(instance)) as Boolean
}
