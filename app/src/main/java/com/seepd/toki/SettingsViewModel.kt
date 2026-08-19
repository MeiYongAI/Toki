package com.seepd.toki

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    private val mutableUiState = MutableStateFlow(repository.load())
    private val mutableHomeUiState = MutableStateFlow(readHomeUiState(application))
    private val mutableRestartStatus = MutableStateFlow(RootActionStatus.IDLE)
    private val mutableCacheClearStatus = MutableStateFlow(RootActionStatus.IDLE)
    private val remoteUpdates = Channel<SettingsUiState>(Channel.CONFLATED)

    val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()
    val homeUiState: StateFlow<HomeUiState> = mutableHomeUiState.asStateFlow()
    val restartStatus: StateFlow<RootActionStatus> = mutableRestartStatus.asStateFlow()
    val cacheClearStatus: StateFlow<RootActionStatus> = mutableCacheClearStatus.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            for (state in remoteUpdates) {
                repository.syncRemote(state)
            }
        }
        repository.connectRemote(
            onConnected = {
                mutableHomeUiState.value = mutableHomeUiState.value.copy(moduleConnected = true)
                syncRemote(mutableUiState.value)
            },
            onDisconnected = {
                mutableHomeUiState.value = mutableHomeUiState.value.copy(moduleConnected = false)
            },
        )
    }

    fun update(transform: (SettingsUiState) -> SettingsUiState) {
        val next = transform(mutableUiState.value)
        if (next == mutableUiState.value) return
        mutableUiState.value = next
        repository.save(next)
        syncRemote(next)
    }

    fun restartTikTok() {
        if (rootActionRunning()) return
        mutableRestartStatus.value = RootActionStatus.RUNNING
        viewModelScope.launch(Dispatchers.IO) {
            mutableRestartStatus.value = RootActions.restartTikTok(getApplication<Application>())
        }
    }

    fun clearTikTokCache() {
        if (rootActionRunning()) return
        mutableCacheClearStatus.value = RootActionStatus.RUNNING
        viewModelScope.launch(Dispatchers.IO) {
            mutableCacheClearStatus.value =
                RootActions.clearTikTokCache(getApplication<Application>())
        }
    }

    fun clearRestartStatus() {
        mutableRestartStatus.value = RootActionStatus.IDLE
    }

    fun clearCacheStatus() {
        mutableCacheClearStatus.value = RootActionStatus.IDLE
    }

    fun setAppLanguage(language: AppLanguage) {
        AppLanguagePreferences.save(getApplication(), language)
        mutableHomeUiState.value = mutableHomeUiState.value.copy(language = language)
    }

    fun resetSettings() {
        val defaults = repository.reset()
        AppLanguagePreferences.reset(getApplication())
        mutableUiState.value = defaults
        mutableHomeUiState.value = mutableHomeUiState.value.copy(language = AppLanguage.SYSTEM)
        syncRemote(defaults)
    }

    fun refreshTikTokStatus() {
        val refreshed = readHomeUiState(getApplication())
        mutableHomeUiState.value = refreshed.copy(
            moduleConnected = mutableHomeUiState.value.moduleConnected,
            language = mutableHomeUiState.value.language,
        )
    }

    private fun syncRemote(state: SettingsUiState) {
        remoteUpdates.trySend(state)
    }

    private fun rootActionRunning(): Boolean =
        mutableRestartStatus.value == RootActionStatus.RUNNING ||
            mutableCacheClearStatus.value == RootActionStatus.RUNNING

    private companion object {
        fun readHomeUiState(application: Application): HomeUiState {
            val packageInfo = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    application.packageManager.getPackageInfo(
                        ModuleConfig.TARGET_PACKAGE,
                        PackageManager.PackageInfoFlags.of(0),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    application.packageManager.getPackageInfo(ModuleConfig.TARGET_PACKAGE, 0)
                }
            } catch (_: PackageManager.NameNotFoundException) {
                null
            } catch (_: RuntimeException) {
                null
            }
            return HomeUiState(
                tikTokInstalled = packageInfo != null,
                tikTokVersion = packageInfo?.versionName,
                language = AppLanguagePreferences.load(application),
            )
        }
    }
}
