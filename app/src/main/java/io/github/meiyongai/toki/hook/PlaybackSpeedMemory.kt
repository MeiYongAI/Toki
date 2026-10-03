package io.github.meiyongai.toki.hook

import android.content.SharedPreferences
import io.github.meiyongai.toki.provider.ConfigSnapshot

/**
 * 宿主拥有的倍速运行偏好；写入不依赖管理端进程，配置导入可明确重置记忆。
 * @param preferences 宿主私有 SharedPreferences，不属于 TikTok 的缓存目录。
 * Callers: PlaybackSpeedHook、PlaybackSpeedMemoryTest。
 */
internal class PlaybackSpeedMemory(private val preferences: SharedPreferences) {
    /**
     * 按配置基准和最近导入版本读取用户在宿主选择的倍速。
     * @param config 完整验证的管理端配置。
     * @return 有效的记忆倍速；未选择或基准改变时使用明确的配置值。
     * Callers: PlaybackSpeedHook.refreshConfig、PlaybackSpeedMemoryTest。
     */
    fun read(config: ConfigSnapshot): Float {
        val configured = config.string("fixed_speed_value", "1.0")!!
        if (preferences.getLong("importRevision", -1) != config.importRevision ||
            preferences.getString("configured", null) != configured) return configured.toFloat()
        val speed = preferences.getFloat("selected", configured.toFloat())
        require(speed.isFinite() && speed in 0.1f..3.0f) { "宿主倍速记忆值无效" }
        return speed
    }

    /**
     * 保存原生菜单选速及其配置基准，供宿主后续进程使用。
     * @param speed 用户选择的合法倍速。
     * @param config 选择时使用的完整配置。
     * @return 存储是否确认成功；失败由调用方报告，不能假装持久化成功。
     * Callers: PlaybackSpeedHook.onUserSelectedSpeed、PlaybackSpeedMemoryTest。
     */
    fun save(speed: Float, config: ConfigSnapshot): Boolean {
        require(speed.isFinite() && speed in 0.1f..3.0f) { "选择的倍速无效" }
        return preferences.edit().putFloat("selected", speed)
            .putString("configured", config.string("fixed_speed_value", "1.0"))
            .putLong("importRevision", config.importRevision).commit()
    }
}
