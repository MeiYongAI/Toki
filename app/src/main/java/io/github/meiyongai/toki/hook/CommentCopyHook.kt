package io.github.meiyongai.toki.hook

import android.content.ClipData
import android.content.Context
import android.util.Log
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.WeakHashMap

/** 在评论复制事务中选取菜单实际显示的译文，保留宿主正文和表情处理。 */
object CommentCopyHook {
    const val KEY_COPY_COMMENT_TEXT_ONLY = "copy_comment_text_only"
    private val menuText = WeakHashMap<Any, CommentCopyText>()
    private val copyScope = CommentCopyScope()

    /** @param context 宿主上下文。@return Unit。Callers: TokiModule.hookApplication。 */
    fun refreshConfig(context: Context) = ConfigClient.init(context)

    /** 由完整 DEX 引用核实全部反射成员及对象关联，安装前拒绝任何缺失或歧义。 */
    internal class Contract(loader: ClassLoader, references: Map<String, String>) {
        val copy: Method
        val clip: Method
        val menu: Method
        val getComment: Method
        val getId: Method
        val item: Field
        val display: Field
        val text: Field
        val extra: Field
        val cid: Field
        val translated: Field
        val itemIndex: Int
        val binderIndex: Int

        init {
            /** @param role 业务角色。@return 所属类型及成员描述。Callers: method、field。 */
            fun owner(role: String): Pair<Class<*>, String> {
                val ref = references.getValue(role)
                val type = ref.substringBefore("->")
                check(type.startsWith('L') && type.endsWith(';') && ref.contains("->")) { "无效评论成员引用：$ref" }
                return Class.forName(type.substring(1, type.length - 1).replace('/', '.'), false, loader) to ref.substringAfter("->")
            }
            /** @param role 方法角色。@return 精确签名匹配的方法。Callers: Contract 初始化。 */
            fun method(role: String): Method {
                val (type, descriptor) = owner(role)
                return type.declaredMethods.single { candidate ->
                    candidate.name + candidate.parameterTypes.joinToString("", "(", ")") { dexType(it) } +
                        dexType(candidate.returnType) == descriptor
                }.apply { isAccessible = true }
            }
            /** @param role 字段角色。@return 精确类型匹配的实例字段。Callers: Contract 初始化。 */
            fun field(role: String): Field {
                val (type, descriptor) = owner(role)
                return type.getDeclaredField(descriptor.substringBefore(':')).apply {
                    check(name + ":" + dexType(this.type) == descriptor && !Modifier.isStatic(modifiers))
                    isAccessible = true
                }
            }
            copy = method("copy"); clip = method("clip"); menu = method("menu"); getComment = method("getComment")
            item = field("item"); display = field("display"); text = field("text"); extra = field("extra")
            cid = field("cid"); translated = field("translated")
            val comment = loader.loadClass("com.ss.android.ugc.aweme.comment.model.Comment")
            getId = comment.getMethod("getCid").apply { check(returnType == String::class.java) }
            check(copy.parameterCount == 0 && copy.returnType == Void.TYPE && !Modifier.isStatic(copy.modifiers))
            check(item.declaringClass == copy.declaringClass && getComment.declaringClass.isAssignableFrom(item.type))
            check(getComment.parameterCount == 0 && getComment.returnType == comment && !Modifier.isStatic(getComment.modifiers))
            check(clip.parameterTypes.contentEquals(arrayOf(String::class.java, String::class.java, List::class.java)) &&
                clip.returnType == ClipData::class.java && Modifier.isStatic(clip.modifiers))
            check(menu.returnType == Void.TYPE && Modifier.isStatic(menu.modifiers))
            check(listOf(text, extra, cid, translated).all { it.declaringClass == display.type })
            check(text.type == String::class.java && cid.type == String::class.java && extra.type == List::class.java &&
                translated.type == Boolean::class.javaPrimitiveType)
            itemIndex = menu.parameterTypes.indices.single { menu.parameterTypes[it] == item.type }
            binderIndex = menu.parameterTypes.indices.single { menu.parameterTypes[it] == display.declaringClass }
            check(itemIndex != binderIndex)
        }
    }

    /** @param type 反射类型。@return 精确 DEX 类型描述。Callers: Contract。 */
    private fun dexType(type: Class<*>): String = when (type) {
        Void.TYPE -> "V"
        Boolean::class.javaPrimitiveType -> "Z"
        Int::class.javaPrimitiveType -> "I"
        Long::class.javaPrimitiveType -> "J"
        Float::class.javaPrimitiveType -> "F"
        Double::class.javaPrimitiveType -> "D"
        Byte::class.javaPrimitiveType -> "B"
        Short::class.javaPrimitiveType -> "S"
        Char::class.javaPrimitiveType -> "C"
        else -> if (type.isArray) type.name.replace('.', '/') else "L${type.name.replace('.', '/')};"
    }

    /**
     * 验证完整评论复制契约后注册；正文与表情快照来自菜单实际绑定的评论。
     * @param module 当前 LSPosed 模块。
     * @param loader 宿主最终类加载器。
     * @return Unit；失败由注册事务撤销，不能部分安装。
     * Callers: TokiModule.onPackageReady。
     */
    fun init(module: XposedModule, loader: ClassLoader) {
        val roles = listOf("copy", "clip", "menu", "getComment", "item", "display", "text", "extra", "cid", "translated")
        val contract = Contract(loader, roles.associateWith { HostSymbols.members(HostSymbol.COMMENT_COPY, it).single() })
        HookRuntime.onDispose("CommentCopyHook") { menuText.clear() }
        module.trackHook("CommentCopyHook", contract.menu).intercept { chain ->
            val item = chain.args[contract.itemIndex]!!
            menuText.remove(item)
            val controller = chain.args[contract.binderIndex]
            if (controller != null && ConfigClient.getBoolean(KEY_COPY_COMMENT_TEXT_ONLY)) {
                val data = contract.display.get(controller)
                if (data != null) {
                    val snapshot = CommentCopyText.displayed(
                        contract.getId.invoke(contract.getComment.invoke(item)) as String?,
                        contract.cid.get(data) as String?, contract.translated.getBoolean(data),
                        contract.text.get(data) as String?, contract.extra.get(data) as List<*>?
                    )
                    if (snapshot != null) menuText[item] = snapshot
                }
            }
            chain.proceed()
        }
        module.trackHook("CommentCopyHook", contract.copy).intercept { chain ->
            if (!ConfigClient.getBoolean(KEY_COPY_COMMENT_TEXT_ONLY)) return@intercept chain.proceed()
            copyScope.within(menuText[contract.item.get(chain.thisObject)]) { chain.proceed() }
        }
        module.trackHook("CommentCopyHook", contract.clip).intercept { chain ->
            if (!ConfigClient.getBoolean(KEY_COPY_COMMENT_TEXT_ONLY)) return@intercept chain.proceed()
            val args = chain.args.toTypedArray()
            args[0] = ""
            copyScope.current?.let {
                args[1] = it.text
                args[2] = it.extra
                HookRuntime.count("CommentCopyHook", "复制显示译文")
            }
            HookRuntime.count("CommentCopyHook", "移除作者前缀")
            chain.proceed(args)
        }
        Log.i("TokiCommentCopy", "评论复制链路已注册：${contract.menu.name} → ${contract.copy.declaringClass.name}.${contract.copy.name}；参数 ${contract.itemIndex}/${contract.binderIndex}")
    }
}
