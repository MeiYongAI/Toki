package com.seepd.toki

internal data class HomeUiState(
    val moduleConnected: Boolean = false,
    val tikTokInstalled: Boolean = false,
    val tikTokVersion: String? = null,
    val language: AppLanguage = AppLanguage.SYSTEM,
)
