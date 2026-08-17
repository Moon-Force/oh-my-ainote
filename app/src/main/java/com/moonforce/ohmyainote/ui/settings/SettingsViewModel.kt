package com.moonforce.ohmyainote.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moonforce.ohmyainote.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val baseUrl: String = "",
    val model: String = "",
    val hasApiKey: Boolean = false,
    val exportPressureVarying: Boolean = true,
    val saved: Boolean = false,
    val error: String? = null,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(container.aiSettingsStore.settings, container.aiSettingsStore.exportPressureVarying) { ai, pressure ->
                SettingsUiState(ai.baseUrl, ai.model, ai.hasApiKey, pressure)
            }.collect { mutableState.value = it }
        }
    }

    fun save(baseUrl: String, model: String, apiKey: String, pressure: Boolean) {
        viewModelScope.launch {
            runCatching {
                container.aiSettingsStore.save(baseUrl, model, apiKey.ifBlank { null })
                container.aiSettingsStore.setExportPressureVarying(pressure)
            }.onSuccess {
                mutableState.update { it.copy(saved = true, error = null, hasApiKey = true, exportPressureVarying = pressure) }
            }.onFailure { failure ->
                mutableState.update { it.copy(error = failure.message ?: "保存失败", saved = false) }
            }
        }
    }

    fun clearKey() {
        viewModelScope.launch {
            container.aiSettingsStore.clearApiKey()
            mutableState.update { it.copy(hasApiKey = false, saved = false) }
        }
    }

    fun consumeSaved() = mutableState.update { it.copy(saved = false) }
    fun clearError() = mutableState.update { it.copy(error = null) }
}
