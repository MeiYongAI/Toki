package io.github.meiyongai.toki.hook

/** 单次同步拦截的异常归属记录；仅以实际调用边界和异常对象身份判断来源。 */
internal class HookInvocation {
    private var downstreamErrors: MutableSet<Throwable>? = null

    /**
     * 执行后续拦截链或宿主方法，记录其抛出的原始异常后立即原样传播。
     * @param call libxposed 的 proceed 或 proceedWith 调用。
     * @return 后续调用的原始返回值。
     * Callers: ObservedChain 的全部 proceed 重载、HookInvocationTest。
     */
    fun proceed(call: () -> Any?): Any? = try {
        call()
    } catch (error: Throwable) {
        val errors = downstreamErrors ?: java.util.Collections.newSetFromMap(
            java.util.IdentityHashMap<Throwable, Boolean>()
        ).also { downstreamErrors = it }
        errors.add(error)
        throw error
    }

    /**
     * 判断逃逸异常是否直接来自后续调用；模块新建或包装的异常仍归属模块。
     * @param error 离开当前拦截器的异常对象。
     * @return true 表示由 proceed 原样传播，不能判定为本模块错误。
     * Callers: TrackedHook.intercept、HookInvocationTest。
     */
    fun fromDownstream(error: Throwable): Boolean = downstreamErrors?.contains(error) == true
}
