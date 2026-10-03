package io.github.meiyongai.toki

import android.app.Application
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.meiyongai.toki.util.LSPosedStatusHelper

/** 管理端进程入口，在界面创建前准备本地设置和框架发布通道。 */
class TokiApplication : Application() {
    /**
     * 初始化进程内唯一配置服务和框架连接监听，不启动宿主应用。
     * @return Unit；无入参。
     * Callers: Android Application 生命周期。
     */
    override fun onCreate() {
        super.onCreate()
        ConfigClient.init(this)
        LSPosedStatusHelper.init()
    }
}
