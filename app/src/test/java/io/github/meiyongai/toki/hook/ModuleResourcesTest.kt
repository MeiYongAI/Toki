package io.github.meiyongai.toki.hook

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.util.AttributeSet
import io.github.meiyongai.toki.R
import io.github.meiyongai.toki.util.AppLanguage
import io.github.meiyongai.toki.util.languageResourceFile
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ClassName
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.RealObject
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowApplicationPackageManager
import org.robolectric.util.ReflectionHelpers.ClassParameter.from
import java.io.File
import java.util.Properties
import java.util.zip.ZipFile
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/** 隐藏包查询后，通过真实 Android Resources 读取编译 APK，覆盖最低和目标系统。 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [28, 35],
    application = Application::class,
    manifest = Config.NONE,
    shadows = [ModuleResourcesTest.HiddenModulePackageManager::class],
)
@LooperMode(LooperMode.Mode.PAUSED)
class ModuleResourcesTest {
    /**
     * Robolectric 默认按包名返回预置资源；这里调用真实 AOSP 的 ApplicationInfo 重载，
     * 让 base / split APK 路径真正经过 AssetManager，同时拒绝模块包的信息查询。
     */
    @Implements(className = "android.app.ApplicationPackageManager", isInAndroidSdk = false)
    class HiddenModulePackageManager : ShadowApplicationPackageManager() {
        @RealObject private lateinit var packageManager: PackageManager

        @Implementation
        override fun getResourcesForApplication(info: ApplicationInfo): Resources =
            Shadow.directlyOn(packageManager, "android.app.ApplicationPackageManager", "getResourcesForApplication",
                from(ApplicationInfo::class.java, info))

        @Implementation
        override fun getApplicationInfo(packageName: String, flags: Int): ApplicationInfo {
            if (packageName == MODULE_PACKAGE) throw PackageManager.NameNotFoundException(packageName)
            return super.getApplicationInfo(packageName, flags)
        }

        @Implementation(minSdk = 33)
        override fun getApplicationInfo(packageName: String,
            @ClassName("android.content.pm.PackageManager\$ApplicationInfoFlags") flags: Any): ApplicationInfo {
            if (packageName == MODULE_PACKAGE) throw PackageManager.NameNotFoundException(packageName)
            return super.getApplicationInfo(packageName, flags)
        }

        @Implementation
        override fun getResourcesForApplication(packageName: String): Resources {
            if (packageName == MODULE_PACKAGE) throw PackageManager.NameNotFoundException(packageName)
            return super.getResourcesForApplication(packageName)
        }
    }

    class HiddenModuleActivity : Activity() {
        override fun createPackageContext(packageName: String, flags: Int): Context {
            if (packageName == MODULE_PACKAGE) throw PackageManager.NameNotFoundException(packageName)
            return super.createPackageContext(packageName, flags)
        }
    }

    private fun moduleResources(): ModuleResources {
        val properties = Properties().apply {
            ModuleResourcesTest::class.java.classLoader!!.getResourceAsStream("com/android/tools/test_config.properties")!!.use(::load)
        }
        val apk = File(properties.getProperty("android_resource_apk")).absoluteFile
        assertTrue("Compiled module resource APK is missing: $apk", apk.isFile)
        ZipFile(apk).use { assertNotNull("Compiled APK must contain an Android resource table", it.getEntry("resources.arsc")) }
        val info = ApplicationInfo().apply {
            packageName = MODULE_PACKAGE
            uid = RuntimeEnvironment.getApplication().applicationInfo.uid + 1
            sourceDir = apk.path
            publicSourceDir = apk.path
        }
        return ModuleResources(info, javaClass.classLoader!!)
    }

    @Test fun hiddenPackageStillLoadsModuleResourcesThemeAndActivityServices() {
        val activityController = Robolectric.buildActivity(HiddenModuleActivity::class.java).setup()
        val activity = activityController.get()
        try {
            val hostFactory = object : LayoutInflater.Factory2 {
                override fun onCreateView(parent: View?, name: String, context: Context, attrs: AttributeSet): View? = null
                override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? = null
            }
            activity.layoutInflater.factory2 = hostFactory
            assertThrows(PackageManager.NameNotFoundException::class.java) {
                activity.packageManager.getApplicationInfo(MODULE_PACKAGE, 0)
            }
            assertThrows(PackageManager.NameNotFoundException::class.java) {
                activity.createPackageContext(MODULE_PACKAGE, 0)
            }
            val context = moduleResources().createActivityContext(activity)
            assertEquals("App language", context.getString(R.string.language_title))
            assertEquals(MODULE_PACKAGE, context.resources.getResourcePackageName(R.string.language_title))
            assertSame(context.resources.assets, context.assets)
            assertSame(context.resources, context.theme.resources)
            assertSame(javaClass.classLoader, context.classLoader)
            assertSame(context.resources, context.applicationContext.resources)
            assertSame(context.classLoader, context.applicationContext.classLoader)
            assertSame(activity.applicationContext, (context.applicationContext as ContextWrapper).baseContext)
            assertSame(activity.getSystemService(Context.WINDOW_SERVICE), context.getSystemService(Context.WINDOW_SERVICE))
            assertSame(context, LayoutInflater.from(context).context)
            assertNotSame(hostFactory, LayoutInflater.from(context).factory2)
            val hostConfiguration = activity.resources.configuration
            val moduleConfiguration = context.resources.configuration
            assertEquals(hostConfiguration.locales, moduleConfiguration.locales)
            assertEquals(hostConfiguration.densityDpi, moduleConfiguration.densityDpi)
            assertEquals(hostConfiguration.fontScale, moduleConfiguration.fontScale, 0f)
            assertEquals(hostConfiguration.screenWidthDp, moduleConfiguration.screenWidthDp)
            assertEquals(hostConfiguration.screenHeightDp, moduleConfiguration.screenHeightDp)
            assertEquals(hostConfiguration.uiMode, moduleConfiguration.uiMode)
        } finally {
            activityController.pause().stop().destroy()
        }
    }

    @Test fun hiddenPackageDialogShowsAndRecreatedActivityUsesNewConfiguration() {
        val resources = moduleResources()
        RuntimeEnvironment.setQualifiers("en")
        val firstController = Robolectric.buildActivity(HiddenModuleActivity::class.java).setup()
        val first = HostScanDialog(firstController.get(), resources) {}
        first.render(HostScanStatus(HostScanPhase.SCANNING, completed = 1, total = 2))
        try {
            first.show()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(first.isShowing)
            assertEquals("App language", first.context.getString(R.string.language_title))
        } finally {
            first.dismiss()
            firstController.pause().stop().destroy()
        }
        RuntimeEnvironment.setQualifiers("zh-rCN")
        val secondController = Robolectric.buildActivity(HiddenModuleActivity::class.java).setup()
        val second = HostScanDialog(secondController.get(), resources) {}
        second.render(HostScanStatus(HostScanPhase.SCANNING, completed = 1, total = 2))
        try {
            second.show()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(second.isShowing)
            assertEquals("界面语言", second.context.getString(R.string.language_title))
            assertNotSame(first.context.resources, second.context.resources)
            assertSame(second.context.resources, second.context.theme.resources)
        } finally {
            second.dismiss()
            secondController.pause().stop().destroy()
        }
    }

    /** 最低和目标系统从真实模块 APK 读取全部 57 种窗口翻译，而不是回退到默认英语。 */
    @Test fun everyHostLanguageLoadsItsOwnMethodDiscoveryStrings() {
        val resources = moduleResources()
        val languages = AppLanguage.entries.filter { it != AppLanguage.SYSTEM }
        assertEquals(57, languages.size)
        for (language in languages) {
            RuntimeEnvironment.setQualifiers("b+${language.languageTag.replace('-', '+')}")
            val controller = Robolectric.buildActivity(HiddenModuleActivity::class.java).setup()
            try {
                val context = resources.createActivityContext(controller.get())
                assertEquals("${language.languageTag}: host locale must be preserved",
                    controller.get().resources.configuration.locales, context.resources.configuration.locales)
                val expected = hostScanTranslations(language)
                for ((name, value) in expected) {
                    val id = context.resources.getIdentifier(name, "string", MODULE_PACKAGE)
                    assertTrue("${language.languageTag}: missing compiled resource $name", id != 0)
                    assertEquals("${language.languageTag}/$name", value, context.getString(id))
                }
            } finally {
                controller.pause().stop().destroy()
            }
        }
    }

    @Test fun unreadableModuleApkFailsWithoutSubstitutingHostResources() {
        val controller = Robolectric.buildActivity(HiddenModuleActivity::class.java).setup()
        try {
            val info = ApplicationInfo().apply {
                packageName = MODULE_PACKAGE
                sourceDir = "/module-apk-does-not-exist/toki.apk"
                publicSourceDir = sourceDir
            }
            val error = assertThrows(Exception::class.java) {
                ModuleResources(info, javaClass.classLoader!!).createActivityContext(controller.get())
            }
            // SDK 28 的 directOn 反射桥会包裹受检异常；最深层仍须是 APK 打开失败。
            val cause = generateSequence(error as Throwable) { it.cause }.last()
            assertTrue(cause.toString(), cause is PackageManager.NameNotFoundException)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    companion object { private const val MODULE_PACKAGE = "io.github.meiyongai.toki" }
}

/** 测试以各语言 XML 为独立预期，不用待验证 Context 读取的字符串反过来证明资源正确。 */
internal fun hostScanTranslations(language: AppLanguage): Map<String, String> {
    val keys = setOf("host_scan_title_running", "host_scan_title_saving", "host_scan_title_complete",
        "host_scan_title_failed", "host_scan_keep_foreground", "host_scan_saving_hint",
        "host_scan_restart_hint", "host_scan_failure_hint", "host_scan_done", "common_close")
    val file = languageResourceFile(language)
    assertTrue("Missing language resource: $file", file.isFile)
    val factory = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        isXIncludeAware = false
        isExpandEntityReferences = false
    }
    val nodes = factory.newDocumentBuilder().parse(file).getElementsByTagName("string")
    val expected = linkedMapOf<String, String>()
    for (index in 0 until nodes.length) {
        val element = nodes.item(index) as Element
        val name = element.getAttribute("name")
        if (name !in keys) continue
        val text = element.textContent
        val value = if (text.startsWith('"') && text.endsWith('"')) text.substring(1, text.length - 1) else text
        expected[name] = Regex("\\\\(.)").replace(value) { match ->
            when (val escaped = match.groupValues[1]) {
                "n" -> "\n"
                "\\", "'", "\"" -> escaped
                else -> error("Unsupported resource escape: $escaped")
            }
        }
    }
    assertEquals("${language.languageTag}: method-discovery translation keys", keys, expected.keys)
    return expected
}
