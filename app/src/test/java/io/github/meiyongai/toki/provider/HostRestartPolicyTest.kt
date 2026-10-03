package io.github.meiyongai.toki.provider

import org.junit.Assert.*
import org.junit.Test

/** 验证管理端手动重启命令的包名、组件及用户编号约束。 */
class HostRestartPolicyTest {
    private val target = "com.zhiliaoapp.musically"

    /** 验证手动重启使用限定组件和明确用户编号。@return Unit。Callers: JUnit。 */
    @Test fun supportedTargetUsesExplicitComponent() {
        assertEquals("am force-stop --user 10 '$target' && am start -W --user 10 -n '$target/.MainActivity'",
            HostRestartPolicy.command(target, "$target/.MainActivity", 10))
    }

    /** 验证不支持的软件包不能成为重启目标。@return Unit。Callers: JUnit。 */
    @Test(expected = IllegalArgumentException::class) fun foreignPackageIsRejected() {
        HostRestartPolicy.command("com.example.unrelated", "com.example.unrelated/.Main", 0)
    }

    /** 验证组件必须属于指定软件包。@return Unit。Callers: JUnit。 */
    @Test(expected = IllegalArgumentException::class) fun otherPackageComponentIsRejected() {
        HostRestartPolicy.command(target, "com.ss.android.ugc.trill/.Main", 0)
    }

    /** 验证用户编号不接受负值。@return Unit。Callers: JUnit。 */
    @Test(expected = IllegalArgumentException::class) fun negativeUserIsRejected() {
        HostRestartPolicy.command(target, "$target/.Main", -1)
    }

    /** 验证组件参数中的Shell字符被拒绝。@return Unit。Callers: JUnit。 */
    @Test(expected = IllegalArgumentException::class) fun commandInjectionIsRejected() {
        HostRestartPolicy.command(target, "$target/.Main'; id", 0)
    }
}
