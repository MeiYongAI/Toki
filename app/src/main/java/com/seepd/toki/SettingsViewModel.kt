package com.seepd.toki

import android.app.Application
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
    private val mutableRestartStatus = MutableStateFlow(RootRestartStatus.IDLE)
    private val remoteUpdates = Channel<SettingsUiState>(Channel.CONFLATED)

    val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()
    val restartStatus: StateFlow<RootRestartStatus> = mutableRestartStatus.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            for (state in remoteUpdates) {
                repository.syncRemote(state)
            }
        }
        repository.connectRemote { syncRemote(mutableUiState.value) }
    }

    fun update(transform: (SettingsUiState) -> SettingsUiState) {
        val next = transform(mutableUiState.value)
        if (next == mutableUiState.value) return
        mutableUiState.value = next
        repository.save(next)
        syncRemote(next)
    }

    fun restartTikTok() {
        if (mutableRestartStatus.value == RootRestartStatus.RUNNING) return
        mutableRestartStatus.value = RootRestartStatus.RUNNING
        viewModelScope.launch(Dispatchers.IO) {
            val result = RootActions.restartTikTok(getApplication())
            mutableRestartStatus.value = if (result == RootRestartStatus.SUCCESS) {
                RootRestartStatus.IDLE
            } else {
                result
            }
        }
    }

    fun clearRestartStatus() {
        mutableRestartStatus.value = RootRestartStatus.IDLE
    }

    private fun syncRemote(state: SettingsUiState) {
        remoteUpdates.trySend(state)
    }
}
