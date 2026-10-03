package io.github.meiyongai.toki.hook

import org.junit.Assert.*
import org.junit.Test

/** 用真实 JVM 反射验证调速成员选择，不依赖 Android 或 TikTok 实例。 */
class PlaybackSpeedMethodTest {
    /** 提供重载方法和调用计数的播放器样本。 */
    open class Manager {
        var calls = 0
        var appliedSpeed = 1f

        /** 应用浮点倍速。@param value 倍速。@return Unit。Callers: 测试反射调用。 */
        open fun renamed(value: Float) { calls++; appliedSpeed = value }

        /** 整数重载不得被选择。@param value 整数。@return Int。Callers: 反射测试。 */
        fun renamed(value: Int): Int = value

        /** 相同参数但返回类型错误。@param value 倍速。@return Float。Callers: 反射测试。 */
        fun wrongReturn(value: Float): Float = value

        /** 进度方法不是调速入口。@param value 进度。@return Unit。Callers: 反射测试。 */
        fun seek(value: Float) { appliedSpeed = value }
    }

    /** 共享父类调速实现的第一个具体播放器。 */
    class First : Manager()

    /** 共享父类调速实现的第二个具体播放器。 */
    class Second : Manager()

    /** 覆盖父类调速方法的具体播放器。 */
    class Overridden : Manager() {
        /** 调用父类并保留独立入口。@param value 倍速。@return Unit。Callers: 反射测试。 */
        override fun renamed(value: Float) { super.renamed(value) }
    }

    /** 没有具体方法体的接口不可直接 Hook。 */
    interface AbstractManager {
        /** 声明倍速接口。@param value 倍速。@return Unit。Callers: 反射测试。 */
        fun renamed(value: Float)
    }

    /** 静态同名方法不是播放器实例入口。 */
    class StaticManager {
        companion object {
            /** 静态方法样本。@param value 倍速。@return Unit。Callers: 反射测试。 */
            @JvmStatic fun renamed(value: Float) { require(value > 0f) }
        }
    }

    /** 验证继承入口返回同一个声明方法，可用于注册去重。@return Unit。Callers: JUnit。 */
    @Test fun inheritedImplementationsShareOneMethod() {
        val first = PlaybackSpeedHook.resolveSpeedMethod(First::class.java, "renamed")
        val second = PlaybackSpeedHook.resolveSpeedMethod(Second::class.java, "renamed")
        assertEquals(Manager::class.java, first.declaringClass)
        assertEquals(first, second)
    }

    /** 验证覆盖实现保留自己的声明类。@return Unit。Callers: JUnit。 */
    @Test fun overriddenImplementationIsSelected() {
        assertEquals(Overridden::class.java,
            PlaybackSpeedHook.resolveSpeedMethod(Overridden::class.java, "renamed").declaringClass)
    }

    /** 验证参数选择及一次调用只执行一次方法体。@return Unit。Callers: JUnit。 */
    @Test fun exactFloatMethodInvokesOnce() {
        val manager = First()
        val method = PlaybackSpeedHook.resolveSpeedMethod(First::class.java, "renamed")
        method.invoke(manager, 1.5f)
        assertEquals(1, manager.calls)
        assertEquals(1.5f, manager.appliedSpeed, 0f)
    }

    /** 验证错误返回类型明确失败。@return Unit。Callers: JUnit。 */
    @Test fun nonVoidReturnIsRejected() {
        assertThrows(IllegalStateException::class.java) {
            PlaybackSpeedHook.resolveSpeedMethod(Manager::class.java, "wrongReturn")
        }
    }

    /** 验证接口抽象声明不被当作具体实现注册。@return Unit。Callers: JUnit。 */
    @Test fun abstractDeclarationIsRejected() {
        assertThrows(IllegalStateException::class.java) {
            PlaybackSpeedHook.resolveSpeedMethod(AbstractManager::class.java, "renamed")
        }
    }

    /** 验证静态方法不被当作实例入口。@return Unit。Callers: JUnit。 */
    @Test fun staticDeclarationIsRejected() {
        assertThrows(IllegalStateException::class.java) {
            PlaybackSpeedHook.resolveSpeedMethod(StaticManager::class.java, "renamed")
        }
    }

    /** 验证入口缺失时不会选择 seek 等其他浮点方法。@return Unit。Callers: JUnit。 */
    @Test fun missingNameDoesNotSelectAnotherFloatMethod() {
        assertThrows(NoSuchMethodException::class.java) {
            PlaybackSpeedHook.resolveSpeedMethod(Manager::class.java, "setSpeed")
        }
    }
}
