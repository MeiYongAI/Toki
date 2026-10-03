package io.github.meiyongai.toki.hook

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.ComponentCallbacks
import android.content.pm.ApplicationInfo
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.util.DisplayMetrics
import android.view.LayoutInflater

/**
 * 由框架已加载的模块信息提供资源来源，避免再次向宿主查询模块包是否可见。
 * ApplicationInfo 重载直接读取 base / split APK，不调用按包名查询的 String 重载。
 * @param applicationInfo libxposed.getModuleApplicationInfo() 返回的模块信息。
 * @param classLoader 已加载模块的类加载器。
 */
internal class ModuleResources(applicationInfo: ApplicationInfo, private val classLoader: ClassLoader) {
    private val moduleInfo = ApplicationInfo(applicationInfo).apply {
        splitSourceDirs = applicationInfo.splitSourceDirs?.clone()
        splitPublicSourceDirs = applicationInfo.splitPublicSourceDirs?.clone()
        sharedLibraryFiles = applicationInfo.sharedLibraryFiles?.clone()
    }

    /**
     * 为本次前台 Activity 创建独立资源和主题，窗口服务仍属于宿主 Activity。
     * 重建 Activity 后使用新的显示参数和配置；API 31 起由 Android 直接创建配置资源。
     */
    @Suppress("DEPRECATION") // API 28 起可用的公开 Resources 构造器，不使用 hidden AssetManager API。
    fun createActivityContext(activity: Activity): Context {
        val configuration = Configuration(activity.resources.configuration)
        val resources = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activity.packageManager.getResourcesForApplication(moduleInfo, configuration)
        } else {
            // API 28–30 尚无接收 Configuration 的公开重载。
            val source = activity.packageManager.getResourcesForApplication(moduleInfo)
            val metrics = DisplayMetrics().apply { setTo(activity.resources.displayMetrics) }
            Resources(source.assets, metrics, configuration)
        }
        return ModuleResourceContext(activity, resources, classLoader)
    }
}

/** 模块资源与类加载器采用同一来源；基底 Context 提供服务和宿主归属信息。 */
private class ModuleResourceContext(
    base: Context,
    private val moduleResources: Resources,
    private val moduleClassLoader: ClassLoader,
) : ContextWrapper(base) {
    private val moduleTheme = moduleResources.newTheme().apply {
        applyStyle(android.R.style.Theme_Material_Light_Dialog_NoActionBar, true)
    }
    private val inflater by lazy(LazyThreadSafetyMode.NONE) {
        // 使用宿主 Application 的标准 inflater，不继承宿主 Activity 的自定义视图工厂。
        LayoutInflater.from(baseContext.applicationContext).cloneInContext(this)
    }
    // Compose 字体加载器会保留 applicationContext：资源来源仍是模块，且不保留 Activity。
    private val moduleApplicationContext by lazy(LazyThreadSafetyMode.NONE) {
        val application = baseContext.applicationContext
        if (application === baseContext) this
        else ModuleResourceContext(application, moduleResources, moduleClassLoader)
    }

    override fun getResources(): Resources = moduleResources
    override fun getAssets(): AssetManager = moduleResources.assets
    override fun getTheme(): Resources.Theme = moduleTheme
    override fun getClassLoader(): ClassLoader = moduleClassLoader
    override fun getApplicationContext(): Context = moduleApplicationContext
    // API 28 的 Context 默认实现会再次调用 applicationContext，包装应用上下文须直接委托。
    override fun registerComponentCallbacks(callback: ComponentCallbacks) = baseContext.registerComponentCallbacks(callback)
    override fun unregisterComponentCallbacks(callback: ComponentCallbacks) = baseContext.unregisterComponentCallbacks(callback)
    override fun getSystemService(name: String): Any? =
        if (name == LAYOUT_INFLATER_SERVICE) inflater else super.getSystemService(name)
}
