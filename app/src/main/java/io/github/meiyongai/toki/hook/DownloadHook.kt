package io.github.meiyongai.toki.hook

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.util.Log
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.libxposed.api.XposedModule
import java.io.File

/**
 * 视频与图片保存增强 Hook（强制无水印下载 + 独立自定义保存路径）。
 *
 * 1. 强制无水印下载：
 *    合并权限解除与无水印转码配置：
 *    - 权限与可见性：官方分享面板与长按菜单按服务端 ACL 判定「保存本地」是否可见及可用，
 *      判定依据为 [ACLCommonShare] 的 code / showType / mute / popupMsg 等属性族。
 *      其中：`code == 0`（操作通过）；`mute == false`（解除禁用置灰）；
 *      `showType == 2`（正常展示启用）；`popupMsg == ""`（清空服务端拦截警告弹窗）。
 *    - 无水印转码：官方 [ACLCommonShare.transcode] 默认 3（带水印），
 *      1 表示无水印（指向 [Video.getDownloadNoWatermarkAddr] 地址族）。
 *    下载选源期间补全缺失的无水印地址，文件名、进度和保存仍由宿主生成。
 *
 * 2. 独立自定义保存路径（视频/图片分类重定向）：
 *    官方保存媒体经 MediaStore 写入相册，携带 `relative_path` 属性。
 *    针对视频（默认 Movies/TikTok）与图片（默认 Pictures/TikTok）分别提供独立的自定义相对路径配置，
 *    在底层写入咽喉通过媒体类型（路径前缀、MIME 类型或文件名扩展名）智能分流并替换为用户配置的目标子路径。
 */
object DownloadHook {

    private const val TAG = "TokiDownloadHook"

    /** 强制无水印下载融合配置键 */
    const val KEY_DOWNLOAD_FORCE_NO_WATERMARK = "download_force_no_watermark"

    /** 自定义保存路径总开关配置键 */
    const val KEY_DOWNLOAD_PATH_ENABLED = "download_path_enabled"

    /** 视频保存相对路径配置键 */
    const val KEY_DOWNLOAD_VIDEO_PATH = "download_video_path"

    /** 图片保存相对路径配置键 */
    const val KEY_DOWNLOAD_IMAGE_PATH = "download_image_path"

    /** 官方默认视频保存相对路径 */
    const val DEFAULT_VIDEO_RELATIVE_PATH = "Movies/TikTok"

    /** 官方默认图片保存相对路径 */
    const val DEFAULT_IMAGE_RELATIVE_PATH = "Pictures/TikTok"

    /** ACLCommonShare.showType 的「正常展示」取值（TikTok 官方规范：0=隐藏不展示, 1=置灰禁用, 2=正常展示） */
    private const val SHOW_TYPE_NORMAL = 2

    /** ACLCommonShare.transcode 的「无水印」取值 */
    private const val TRANSCODE_NO_WATERMARK = 1

    /** MediaStore RELATIVE_PATH 合法一级目录（公共媒体根） */
    private val PRIMARY_MEDIA_DIRS = io.github.meiyongai.toki.provider.ConfigSchema.publicMediaDirectories

    /** 图片常见扩展名 */
    private val IMAGE_EXTENSIONS = setOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp")

    /**
     * 读取强制无水印下载开关状态。
     *
     * Returns:
     *     Boolean: 开关是否启用。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.DownloadHook.hookAclCommonShare`: 统一权限与转码 Getter 拦截。
     */
    private fun forceDownloadEnabled(): Boolean =
        ConfigClient.getBoolean(KEY_DOWNLOAD_FORCE_NO_WATERMARK)

    /**
     * 读取自定义保存路径总开关状态。
     *
     * Returns:
     *     Boolean: 开关是否启用。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.DownloadHook.hookMediaStoreRelativePath`: 写入相对路径拦截。
     *     - `io.github.meiyongai.toki.hook.DownloadHook.hookLegacyPublicDirectory`: 获取公共目录拦截。
     */
    private fun pathOverrideEnabled(): Boolean =
        ConfigClient.getBoolean(KEY_DOWNLOAD_PATH_ENABLED)

    /**
     * 初始化挂载全部媒体保存增强 Hook，直接从上游数据模型层面彻底解除限制并重定向路径。
     *
     * Args:
     *     module (XposedModule): LSPosed 现代化模块注入上下文实例。
     *     classLoader (ClassLoader): 宿主目标应用的核心类加载器。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.TokiModule.onPackageLoaded`: 目标应用包完成加载时触发注册。
     */
    fun init(module: XposedModule, classLoader: ClassLoader) {
        if (forceDownloadEnabled()) hookDownloadSource(module, classLoader)
        hookAclCommonShare(module, classLoader)
        hookMediaStoreRelativePath(module)
        hookLegacyPublicDirectory(module)
        Log.i(TAG, "媒体保存增强 Hook 初始化挂载就绪")
    }

    /**
     * 官方选源可能因 ug_download_remove_play_addr 放弃播放源。只在无水印选源调用内
     * 让空地址 Getter 返回该视频已有的完整播放源，原方法继续生成正确文件名和下载任务。
     */
    private fun hookDownloadSource(module: XposedModule, loader: ClassLoader) {
        val selector = HostSymbols.resolve(loader, HostSymbol.DOWNLOAD_SOURCE)
        val awemeClass = loader.loadClass("com.ss.android.ugc.aweme.feed.model.Aweme")
        val videoClass = loader.loadClass("com.ss.android.ugc.aweme.feed.model.Video")
        val urlClass = loader.loadClass("com.ss.android.ugc.aweme.base.model.UrlModel")
        val videoUrlClass = loader.loadClass("com.ss.android.ugc.aweme.feed.model.VideoUrlModel")
        val getVideo = awemeClass.getMethod("getVideo")
        val getUrls = urlClass.getMethod("getUrlList")
        val hasDash = videoUrlClass.getMethod("hasDashBitrate")
        // 原始 H264 Getter 不经过下面的 Hook，避免递归；随后使用宿主实际播放源。
        val playGetters = listOf("getRawPlayAddr\$common_model_release", "getPlayAddr", "getPlayAddrBytevc1")
            .map { videoClass.getMethod(it) }
        val usable: (Any) -> Boolean = { value ->
            val urls = getUrls.invoke(value) as? List<*>
            urls != null && urls.isNotEmpty() && urls.all { it is String && (it.startsWith("https://") || it.startsWith("http://")) } &&
                !(videoUrlClass.isInstance(value) && hasDash.invoke(value) == true)
        }
        val scope = DownloadSourceScope()
        for (name in listOf("getDownloadNoWatermarkAddr", "getPlayAddrH264")) {
            val getter = videoClass.getMethod(name)
            module.trackHook("DownloadHook", getter).intercept { chain ->
                val original = chain.proceed()
                if (!forceDownloadEnabled()) return@intercept original
                scope.resolve(chain.thisObject, original, usable) {
                    playGetters.asSequence().mapNotNull { method ->
                        method.invoke(chain.thisObject)?.takeIf { getter.returnType.isInstance(it) && usable(it) }
                            ?.also { Log.i(TAG, "无水印选源：$name 缺少有效地址，使用 ${method.name}") }
                    }.firstOrNull()
                }
            }
        }
        val select = selector.getDeclaredMethod(HostSymbols.member(HostSymbol.DOWNLOAD_SOURCE, "select"),
            awemeClass, Boolean::class.javaPrimitiveType)
        // 选源 Getter 可能已被 ART 内联，确保原方法执行时经过上述拦截。
        module.deoptimize(select)
        module.trackHook("DownloadHook", select).intercept { chain ->
            if (!forceDownloadEnabled() || chain.args[1] != true) return@intercept chain.proceed()
            scope.selecting(getVideo.invoke(chain.args[0])) { chain.proceed() }
        }
    }

    /**
     * 目标应用 Application 上下文下的配置刷新同步入口。
     *
     * Args:
     *     context (Context): 目标应用上下文环境。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.TokiModule.hookApplication`: 宿主冷启动完成时分发。
     */
    fun refreshConfig(context: Context) {
        // 配置由 ConfigClient 统一管理并缓存
    }

    /**
     * 将用户输入整理为 MediaStore 合法相对路径。
     *
     * 规则：统一分隔符并压缩空段；禁止 `..` 上跳；一级目录必须是公共媒体
     * 根之一，非法或为空时回落指定的默认路径；仅输入一级目录时自动补全子路径。
     *
     * Args:
     *     raw (String?): 用户原始输入。
     *     fallbackDefault (String): 当输入无效时的默认回退路径。
     *
     * Returns:
     *     String: 规范化后的相对路径。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.DownloadHook.resolveConfiguredVideoPath`: 读取视频配置时调用。
     *     - `io.github.meiyongai.toki.hook.DownloadHook.resolveConfiguredImagePath`: 读取图片配置时调用。
     *     - `io.github.meiyongai.toki.ui.MainActivity`: 保存输入前预校验。
     */
    fun sanitizeRelativePath(raw: String?, fallbackDefault: String): String {
        if (raw.isNullOrBlank()) return fallbackDefault
        val segments = raw.trim()
            .replace('\\', '/')
            .split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "." }
        if (segments.isEmpty() || segments.any { it == ".." }) return fallbackDefault
        if (segments[0] !in PRIMARY_MEDIA_DIRS) return fallbackDefault
        return if (segments.size == 1) {
            val fallbackTail = fallbackDefault.substringAfter('/', "TikTok")
            "${segments[0]}/$fallbackTail"
        } else {
            segments.joinToString("/")
        }
    }

    /**
     * 读取当前配置的自定义视频相对路径。
     *
     * Returns:
     *     String: 规范化后的视频保存路径。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.DownloadHook.redirectRelativePath`: 视频路径重定向。
     *     - `io.github.meiyongai.toki.hook.DownloadHook.hookLegacyPublicDirectory`: 公共目录改写。
     */
    fun resolveConfiguredVideoPath(): String =
        sanitizeRelativePath(
            ConfigClient.getString(KEY_DOWNLOAD_VIDEO_PATH, DEFAULT_VIDEO_RELATIVE_PATH),
            DEFAULT_VIDEO_RELATIVE_PATH
        )

    /**
     * 读取当前配置的自定义图片相对路径。
     *
     * Returns:
     *     String: 规范化后的图片保存路径。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.DownloadHook.redirectRelativePath`: 图片路径重定向。
     *     - `io.github.meiyongai.toki.hook.DownloadHook.hookLegacyPublicDirectory`: 公共目录改写。
     */
    fun resolveConfiguredImagePath(): String =
        sanitizeRelativePath(
            ConfigClient.getString(KEY_DOWNLOAD_IMAGE_PATH, DEFAULT_IMAGE_RELATIVE_PATH),
            DEFAULT_IMAGE_RELATIVE_PATH
        )

    /**
     * 判定给定上下文是否属于图片保存操作。
     *
     * Args:
     *     originalPath (String): 宿主传入的原始 relative_path。
     *     contentValues (ContentValues): 当前正被改写的 ContentValues 实例。
     *
     * Returns:
     *     Boolean: 是否为图片媒体。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.DownloadHook.redirectRelativePath`: 媒体分类判定。
     */
    private fun isImageMedia(originalPath: String, contentValues: ContentValues): Boolean {
        val root = originalPath.trim().trimStart('/').substringBefore('/')
        if (root.equals(Environment.DIRECTORY_PICTURES, ignoreCase = true)) return true
        if (root.equals(Environment.DIRECTORY_MOVIES, ignoreCase = true)) return false

        val mime = contentValues.getAsString("mime_type")
        if (mime?.startsWith("image/", ignoreCase = true) == true) return true
        if (mime?.startsWith("video/", ignoreCase = true) == true) return false

        val displayName = contentValues.getAsString("_display_name")?.lowercase()
        if (displayName != null && IMAGE_EXTENSIONS.any { displayName.endsWith(it) }) return true

        return false
    }

    /**
     * 将宿主原始相对路径按媒体类型分类重定向至对应的用户配置路径。
     *
     * Args:
     *     original (String): 宿主原始写入的 relative_path。
     *     contentValues (ContentValues): 宿主填充的 ContentValues 实例。
     *
     * Returns:
     *     String: 重定向后的相对路径。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.DownloadHook.hookMediaStoreRelativePath`: 写入拦截改写。
     */
    private fun redirectRelativePath(original: String, contentValues: ContentValues): String {
        return if (isImageMedia(original, contentValues)) {
            resolveConfiguredImagePath()
        } else {
            resolveConfiguredVideoPath()
        }
    }

    /**
     * 挂载 ACLCommonShare 上游权限控制与无水印转码拦截。
     *
     * 在上游数据实体类拦截核心字段，实现原子化无限制无水印下载：
     * 1. [getTranscode]：开启时返回 1（无水印超清源）。
     * 2. [getCode]：开启时返回 0（操作放行）。
     * 3. [getMute]：开启时返回 false（解除置灰）。
     * 4. [getShowType]：开启时返回 2（正常展示并启用）。
     * 5. [getPopupMsg]：开启时返回空字符串（清空拦截弹窗）。
     *
     * Args:
     *     module (XposedModule): LSPosed 现代化模块注入上下文实例。
     *     classLoader (ClassLoader): 宿主目标应用的核心类加载器。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.DownloadHook.init`: 模块初始化阶段挂载。
     */
    private fun hookAclCommonShare(module: XposedModule, classLoader: ClassLoader) {
        val aclClass = classLoader.loadClass("com.ss.android.ugc.aweme.feed.model.ACLCommonShare")

        module.trackHook("DownloadHook", aclClass.getMethod("getTranscode")).intercept { chain ->
            val result = chain.proceed()
            if (forceDownloadEnabled() && result is Int && result != TRANSCODE_NO_WATERMARK) {
                TRANSCODE_NO_WATERMARK
            } else {
                result
            }
        }

        module.trackHook("DownloadHook", aclClass.getMethod("getMute")).intercept { chain ->
            if (forceDownloadEnabled()) false else chain.proceed()
        }
        module.trackHook("DownloadHook", aclClass.getMethod("getCode")).intercept { chain ->
            if (forceDownloadEnabled()) 0 else chain.proceed()
        }
        module.trackHook("DownloadHook", aclClass.getMethod("getShowType")).intercept { chain ->
            if (forceDownloadEnabled()) SHOW_TYPE_NORMAL else chain.proceed()
        }
        module.trackHook("DownloadHook", aclClass.getMethod("getPopupMsg")).intercept { chain ->
            if (forceDownloadEnabled()) "" else chain.proceed()
        }
        Log.i(TAG, "已挂载 ACLCommonShare 强制无水印下载拦截（开关实时生效）")
    }

    /**
     * 挂载 MediaStore relative_path 写入拦截（Android 10+ 媒体存储相对路径改写）。
     *
     * 仅在自定义路径开关开启、键为 relative_path 且原值为公共媒体路径时
     * 改写，其余调用按原参数放行，对宿主其余数据库与媒体操作零扰动。
     *
     * Args:
     *     module (XposedModule): LSPosed 现代化模块注入上下文实例。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.DownloadHook.init`: 模块初始化阶段挂载。
     */
    private fun hookMediaStoreRelativePath(module: XposedModule) {
        val putMethod = ContentValues::class.java.getMethod(
            "put",
            String::class.java,
            String::class.java
        )
        val relativePathKey = "relative_path"
        module.trackHook("DownloadHook", putMethod).intercept { chain ->
            val args = chain.args
            val key = args.getOrNull(0) as? String
            val value = args.getOrNull(1) as? String
            val thisCv = chain.thisObject as? ContentValues
            if (pathOverrideEnabled() && key == relativePathKey && !value.isNullOrBlank() &&
                isPublicMediaRelativePath(value) && thisCv != null
            ) {
                chain.proceed(arrayOf(key, redirectRelativePath(value, thisCv)))
            } else {
                chain.proceed()
            }
        }
        Log.i(TAG, "已挂载 ContentValues relative_path 分类重定向拦截（开关实时生效）")
    }

    /**
     * 挂载旧版本（Android 10 以下）公共媒体目录直写拦截。
     *
     * Args:
     *     module (XposedModule): LSPosed 现代化模块注入上下文实例。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.DownloadHook.init`: 模块初始化阶段挂载。
     */
    private fun hookLegacyPublicDirectory(module: XposedModule) {
        val getPublicDir = Environment::class.java.getMethod(
            "getExternalStoragePublicDirectory",
            String::class.java
        )
        module.trackHook("DownloadHook", getPublicDir).intercept { chain ->
            val type = chain.args.getOrNull(0) as? String
            val original = chain.proceed() as? File
            if (pathOverrideEnabled() && type != null && type in PRIMARY_MEDIA_DIRS) {
                val targetRelativePath = if (type.equals(Environment.DIRECTORY_PICTURES, ignoreCase = true)) {
                    resolveConfiguredImagePath()
                } else {
                    resolveConfiguredVideoPath()
                }
                val externalRoot = Environment.getExternalStorageDirectory()
                if (externalRoot != null) {
                    File(externalRoot, targetRelativePath)
                } else {
                    original
                }
            } else {
                original
            }
        }
        Log.i(TAG, "已挂载 Environment 公共目录分类重定向拦截（开关实时生效）")
    }

    /**
     * 判断给定相对路径是否为公共媒体目录族（允许重定向的范围）。
     *
     * Args:
     *     path (String): 待判定相对路径。
     *
     * Returns:
     *     Boolean: 是否为公共媒体路径。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.DownloadHook.hookMediaStoreRelativePath`: 拦截判定。
     */
    private fun isPublicMediaRelativePath(path: String): Boolean {
        val root = path.trim().trimStart('/').substringBefore('/')
        return root in PRIMARY_MEDIA_DIRS
    }
}
