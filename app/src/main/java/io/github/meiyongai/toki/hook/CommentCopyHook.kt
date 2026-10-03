package io.github.meiyongai.toki.hook

import android.content.ClipData
import android.content.Context
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Modifier
import java.util.WeakHashMap

/** 在评论复制事务中选取菜单实际显示的译文，保留宿主正文和表情处理。 */
object CommentCopyHook {
    const val KEY_COPY_COMMENT_TEXT_ONLY = "copy_comment_text_only"
    private val menuText = WeakHashMap<Any, CommentCopyText>()
    private val copyScope = CommentCopyScope()

    /**
     * 初始化共享配置；每次复制直接读取最新快照，不另存功能开关。
     * @param context 已附加的宿主上下文。
     * @return Unit。
     * Callers: TokiModule.hookApplication。
     */
    fun refreshConfig(context: Context) = ConfigClient.init(context)

    /**
     * 注册已验证的评论剪贴板构造函数，不拦截资源查询或通用剪贴板写入。
     * @param module libxposed 模块。
     * @param loader 宿主最终类加载器。
     * @return Unit；契约不匹配时由注册事务明确报告失败。
     * Callers: TokiModule.onPackageReady。
     */
    fun init(module: XposedModule, loader: ClassLoader) {
        val action = HostSymbols.resolve(loader, HostSymbol.COMMENT_ACTION)
        val menu = HostSymbols.resolve(loader, HostSymbol.COMMENT_MENU)
        val binder = HostSymbols.resolve(loader, HostSymbol.COMMENT_BINDER)
        val display = HostSymbols.resolve(loader, HostSymbol.COMMENT_DISPLAY)
        val itemField = action.getDeclaredField(HostSymbols.member(HostSymbol.COMMENT_ACTION, "item")).apply { isAccessible = true }
        val comment = loader.loadClass("com.ss.android.ugc.aweme.comment.model.Comment")
        val readComment = itemField.type.getMethod("LIZ").apply { check(returnType == comment) }
        val readId = comment.getMethod("getCid")
        val displayField = binder.getDeclaredField(HostSymbols.member(HostSymbol.COMMENT_BINDER, "display")).apply {
            check(type == display)
            isAccessible = true
        }
        val textField = display.getDeclaredField(HostSymbols.member(HostSymbol.COMMENT_DISPLAY, "text")).apply { isAccessible = true }
        val extraField = display.getDeclaredField(HostSymbols.member(HostSymbol.COMMENT_DISPLAY, "extra")).apply { isAccessible = true }
        val idField = display.getDeclaredField(HostSymbols.member(HostSymbol.COMMENT_DISPLAY, "cid")).apply { isAccessible = true }
        val translatedField = display.getDeclaredField(HostSymbols.member(HostSymbol.COMMENT_DISPLAY, "translated")).apply { isAccessible = true }
        val menuOpen = menu.declaredMethods.single { it.name == HostSymbols.member(HostSymbol.COMMENT_MENU, "open") }.apply {
            check(Modifier.isStatic(modifiers) && parameterCount == 15 && parameterTypes[1] == itemField.type && parameterTypes[7] == binder)
            isAccessible = true
        }
        module.trackHook("CommentCopyHook", menuOpen).intercept { chain ->
            val item = chain.args[1]!!
            menuText.remove(item)
            val controller = chain.args[7]
            if (controller != null && ConfigClient.getBoolean(KEY_COPY_COMMENT_TEXT_ONLY)) {
                val data = displayField.get(controller)
                if (data != null) {
                    val snapshot = CommentCopyText.displayed(
                        readId.invoke(readComment.invoke(item)) as String?,
                        idField.get(data) as String?,
                        translatedField.getBoolean(data),
                        textField.get(data) as String?,
                        extraField.get(data) as List<*>?
                    )
                    if (snapshot != null) menuText[item] = snapshot
                }
            }
            chain.proceed()
        }
        val copy = action.getDeclaredMethod(HostSymbols.member(HostSymbol.COMMENT_ACTION, "copy"))
        module.trackHook("CommentCopyHook", copy).intercept { chain ->
            if (!ConfigClient.getBoolean(KEY_COPY_COMMENT_TEXT_ONLY)) return@intercept chain.proceed()
            val item = itemField.get(chain.thisObject)
            copyScope.within(menuText[item]) { chain.proceed() }
        }
        val method = HostSymbols.resolve(loader, HostSymbol.COMMENT_CLIP).getDeclaredMethod(
            "LIZ", String::class.java, String::class.java, List::class.java
        ).apply {
            check(returnType == ClipData::class.java && Modifier.isStatic(modifiers))
            isAccessible = true
        }
        module.trackHook("CommentCopyHook", method).intercept { chain ->
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
    }
}
