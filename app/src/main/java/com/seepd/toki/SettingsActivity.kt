package com.seepd.toki

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class SettingsActivity : ComponentActivity() {
    private val settingsViewModel by viewModels<SettingsViewModel>()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguagePreferences.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
            val homeUiState by settingsViewModel.homeUiState.collectAsStateWithLifecycle()
            val restartStatus by settingsViewModel.restartStatus.collectAsStateWithLifecycle()
            val cacheClearStatus by settingsViewModel.cacheClearStatus.collectAsStateWithLifecycle()
            TokiTheme {
                SettingsApp(
                    state = uiState,
                    homeState = homeUiState,
                    onUpdate = settingsViewModel::update,
                    restartStatus = restartStatus,
                    cacheClearStatus = cacheClearStatus,
                    onRestartTikTok = settingsViewModel::restartTikTok,
                    onClearTikTokCache = settingsViewModel::clearTikTokCache,
                    onRestartStatusConsumed = settingsViewModel::clearRestartStatus,
                    onCacheClearStatusConsumed = settingsViewModel::clearCacheStatus,
                    onLanguageSelected = { language ->
                        settingsViewModel.setAppLanguage(language)
                        recreate()
                    },
                    onResetSettings = {
                        settingsViewModel.resetSettings()
                        recreate()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        settingsViewModel.refreshTikTokStatus()
    }
}
