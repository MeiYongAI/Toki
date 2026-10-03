package io.github.meiyongai.toki.provider

/** 限制管理端手动重启的目标包与启动组件，禁止任意命令参数。 */
internal object HostRestartPolicy {
    val packages = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")

    /**
     * 生成仅包含已验证组件和用户编号的重启命令，不接受任意 Shell 参数。
     * @param target 已支持的软件包。
     * @param component 系统 PackageManager 返回的完整组件名。
     * @param userId Toki 所在 Android 用户编号。
     * @return 先停止目标再启动明确组件的命令。
     * Callers: RootCommandExecutor.restartPackage、单元测试。
     */
    fun command(target: String, component: String, userId: Int): String {
        require(target in packages && userId >= 0)
        require(component.startsWith("$target/") && component.matches(Regex("[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+"))) {
            "启动组件不属于目标应用或包含非法字符"
        }
        return "am force-stop --user $userId '$target' && am start -W --user $userId -n '$component'"
    }
}
