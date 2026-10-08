package io.github.meiyongai.toki.hook

/** 为复用生命周期入口的原生组件确定唯一语义，具体子类优先于通用横幅。 */
internal object LayoutComponentSelector {
    /**
     * 沿实际实例的类继承链选择最近的已登记类型，不受 Hook 入口或目录顺序影响。
     * @param instance 当前接收生命周期回调的组件。
     * @param entries 类与净化语义的目录；仅接受具体类，不接受接口。
     * @return 最近的登记语义；未登记实例返回 null。
     * Callers: LayoutCleanupHook.init 生命周期拦截器、LayoutComponentSelectorTest。
     */
    fun <T> select(instance: Any, entries: List<Pair<Class<*>, T>>): T? {
        var type: Class<*>? = instance.javaClass
        while (type != null) {
            entries.singleOrNull { it.first == type }?.let { return it.second }
            type = type.superclass
        }
        return null
    }
}
