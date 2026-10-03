package io.github.meiyongai.toki.hook

import android.app.Application
import android.content.ClipData
import com.ss.android.ugc.aweme.comment.model.Comment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 注册前验证类型关系，不因菜单参数移动或成员改名而读错评论。 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [35])
class CommentCopyContractTest {
    open class BaseItem {
        /** @return 评论。无参数。Callers: 反射契约测试。 */
        fun renamedGetter(): Comment = Comment()
    }
    class Item : BaseItem()
    class Display {
        @JvmField var body = "translated"
        @JvmField var emojis: List<*>? = null
        @JvmField var identity = "test-comment"
        @JvmField var translated = true
    }
    class Binder { @JvmField var rendered = Display() }
    class Action {
        @JvmField var target = Item()
        /** 复制入口。无参数，无返回。Callers: 反射契约测试。 */
        fun renamedCopy() {}
    }
    class Host {
        companion object {
            /** @param binder 显示控制器。@param unrelated 无关参数。@param item 评论项。@return Unit。Callers: 反射契约测试。 */
            @JvmStatic fun renamedMenu(binder: Binder, unrelated: String, item: Item) {}
            /** @param first 评论项。@param second 歧义参数。@param binder 控制器。@return Unit。Callers: 反射契约测试。 */
            @JvmStatic fun ambiguousMenu(first: Item, second: Item, binder: Binder) {}
            /** @param prefix 作者前缀。@param text 正文。@param extra 表情。@return 剪贴板。Callers: 反射契约测试。 */
            @JvmStatic fun renamedClip(prefix: String, text: String, extra: List<*>): ClipData = ClipData.newPlainText("label", text)
        }
    }
    /** @param type 类。@return DEX 类名。Callers: references、tests。 */
    private fun type(type: Class<*>) = "L${type.name.replace('.', '/')};"

    /** @return 重命名后的完整 DEX 成员集合。无参数。Callers: tests。 */
    private fun references(): MutableMap<String, String> {
        val action = type(Action::class.java); val item = type(Item::class.java)
        val binder = type(Binder::class.java); val display = type(Display::class.java)
        val host = type(Host::class.java)
        return mutableMapOf(
            "copy" to "$action->renamedCopy()V", "item" to "$action->target:$item",
            "getComment" to "${type(BaseItem::class.java)}->renamedGetter()${type(Comment::class.java)}",
            "menu" to "$host->renamedMenu(${binder}Ljava/lang/String;$item)V",
            "clip" to "$host->renamedClip(Ljava/lang/String;Ljava/lang/String;Ljava/util/List;)Landroid/content/ClipData;",
            "display" to "$binder->rendered:$display", "text" to "$display->body:Ljava/lang/String;",
            "extra" to "$display->emojis:Ljava/util/List;", "cid" to "$display->identity:Ljava/lang/String;",
            "translated" to "$display->translated:Z"
        )
    }
    /** 父类 getter、重命名成员与移动后的参数均按关系解析。无参数，无返回。Callers: JUnit。 */
    @Test fun renamedMembersAndReorderedArgumentsAreValidated() {
        val contract = CommentCopyHook.Contract(javaClass.classLoader!!, references())
        assertEquals(2, contract.itemIndex)
        assertEquals(0, contract.binderIndex)
        assertEquals("renamedGetter", contract.getComment.name)
        assertEquals("body", contract.text.name)
    }
    /** 缺失成员、错误类型、重复参数不能产生半套注册。无参数，无返回。Callers: JUnit。 */
    @Test fun incompleteOrAmbiguousContractsAreRejectedBeforeRegistration() {
        val missing = references().apply { remove("extra") }
        assertThrows(NoSuchElementException::class.java) { CommentCopyHook.Contract(javaClass.classLoader!!, missing) }
        val wrongType = references().apply { this["text"] = "${type(Display::class.java)}->body:Z" }
        assertThrows(IllegalStateException::class.java) { CommentCopyHook.Contract(javaClass.classLoader!!, wrongType) }
        val ambiguous = references().apply { this["menu"] = "${type(Host::class.java)}->ambiguousMenu(${type(Item::class.java)}${type(Item::class.java)}${type(Binder::class.java)})V" }
        assertThrows(IllegalArgumentException::class.java) { CommentCopyHook.Contract(javaClass.classLoader!!, ambiguous) }
    }
}
