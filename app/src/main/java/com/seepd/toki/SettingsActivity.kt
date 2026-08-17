package com.seepd.toki

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
            val restartStatus by settingsViewModel.restartStatus.collectAsStateWithLifecycle()
            TokiTheme {
                SettingsApp(
                    state = uiState,
                    onUpdate = settingsViewModel::update,
                    restartStatus = restartStatus,
                    onRestartTikTok = settingsViewModel::restartTikTok,
                    onRestartStatusConsumed = settingsViewModel::clearRestartStatus,
                )
            }
        }
    }
}
