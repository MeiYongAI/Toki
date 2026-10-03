package io.github.meiyongai.toki.util

import android.content.Context
import android.content.pm.PackageManager

/**
 * TikTok 宿主应用安装状态及版本信息封装模型。
 *
 * @property isInstalled 目标应用是否已安装在当前系统中。
 * @property packageName 检测到的目标宿主包名。
 * @property versionName 目标应用版本名称，若未安装则为 null。
 * @property versionCode 目标应用内部构建版本号，若未安装则为 0L。
 */
data class TikTokInstallStatus(
    val isInstalled: Boolean,
    val packageName: String? = null,
    val versionName: String? = null,
    val versionCode: Long = 0L
)

/**
 * TikTok 宿主应用版本与适配支持信息助手。
 *
 * 负责通过系统 [PackageManager] 探测 TikTok（含国际主流版与区域版本）的安装情况，
 * 并提供当前模块经过适配验证的支持版本清单。
 */
object TikTokVersionHelper {

    /**
     * 目标应用可能采用的包名清单（优先探测主流国际版）。
     */
    private val CANDIDATE_PACKAGES = listOf(
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill"
    )

    /**
     * 当前模块适配验证支持的稳定 TikTok 版本清单。
     */
    val SUPPORTED_VERSIONS: List<String> = listOf(
        "v47.0.3",
        "v46.8.3"
    )

    /**
     * 查询本地设备中已安装的 TikTok 目标应用版本信息。
     *
     * 遍历候选宿主包名列表，若匹配到已安装应用则立即解析其版本名称与构建号并返回；
     * 若均未安装，则明确返回未安装状态对象。
     *
     * @param context 系统上下文环境。
     * @return [TikTokInstallStatus] 封装了安装状态与版本号的对象。
     *
     * Callers:
     * - `io.github.meiyongai.toki.ui.screen.HomeScreen`: 首页顶部 TikTok 状态卡片展示。
     */
    fun getInstalledTikTokStatus(context: Context): TikTokInstallStatus {
        val packageManager = context.packageManager
        for (pkg in CANDIDATE_PACKAGES) {
            try {
                val packageInfo = packageManager.getPackageInfo(pkg, 0)
                val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }
                return TikTokInstallStatus(
                    isInstalled = true,
                    packageName = pkg,
                    versionName = packageInfo.versionName,
                    versionCode = versionCode
                )
            } catch (_: PackageManager.NameNotFoundException) {
                // 当前候选包名未在此设备安装，继续探测下一个候选包名
            }
        }
        return TikTokInstallStatus(isInstalled = false)
    }
}
