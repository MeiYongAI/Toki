package io.github.meiyongai.toki.hook

import org.junit.Assert.*
import org.junit.Test

/** 验证作者显示入口完整性与地区格式，不加载或改写宿主模型。 */
class AuthorLocationContractTest {
    class User {
        /** @return 地区代码。无参数。Callers: 反射契约测试。 */
        fun getRegion(): String = "US"
    }
    class Video {
        /** @return 作者。无参数。Callers: 反射契约测试。 */
        fun getAuthor(): User = User()
    }
    class Builder {
        companion object {
            /** @param text 文本。@param user 作者。@param video 视频。@return 显示文本。Callers: 反射契约测试。 */
            @JvmStatic fun renamed(text: String?, user: User?, video: Video?): String = text.orEmpty()
            /** @param text 文本。@param user 作者。@param video 视频。@return 不符合契约的类型。Callers: 反射契约测试。 */
            @JvmStatic fun invalid(text: String?, user: User?, video: Video?): Int = 0
        }
    }
    /** 改名入口可解析；缺失或错误返回类型必须在注册前拒绝。无参数，无返回。Callers: JUnit。 */
    @Test fun renamedMethodResolvesAndInvalidContractFails() {
        val contract = AuthorLocationHook.Contract(Builder::class.java, "renamed", User::class.java, Video::class.java)
        assertEquals("renamed", contract.build.name)
        assertEquals(User::class.java, contract.author.returnType)
        assertThrows(NoSuchMethodException::class.java) {
            AuthorLocationHook.Contract(Builder::class.java, "missing", User::class.java, Video::class.java)
        }
        assertThrows(IllegalStateException::class.java) {
            AuthorLocationHook.Contract(Builder::class.java, "invalid", User::class.java, Video::class.java)
        }
    }
    /** 空地区不添加标识，格式化幂等且保留原始昵称内容。无参数，无返回。Callers: JUnit。 */
    @Test fun formattingPreservesNameAndAvoidsDuplicatePrefix() {
        val name = "作者 🌸"
        assertEquals(name, AuthorLocationHook.formatAuthorWithLocation(name, " "))
        val formatted = AuthorLocationHook.formatAuthorWithLocation(name, " us ")
        assertEquals("[🇺🇸US] $name", formatted)
        assertEquals(formatted, AuthorLocationHook.formatAuthorWithLocation(formatted, "US"))
        assertEquals("[🌐Unknown] $name", AuthorLocationHook.formatAuthorWithLocation(name, "Unknown"))
    }
}
