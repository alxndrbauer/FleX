package com.vrema.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vrema.domain.model.QuotaRule
import com.vrema.domain.model.Settings
import com.vrema.domain.repository.SettingsRepository
import com.vrema.domain.usecase.GetSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getSettings: GetSettingsUseCase,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _quotaRules = MutableStateFlow<List<QuotaRule>>(emptyList())
    val quotaRules: StateFlow<List<QuotaRule>> = _quotaRules.asStateFlow()

    init {
        viewModelScope.launch {
            getSettings().collect { _settings.value = it }
        }
        viewModelScope.launch {
            settingsRepository.getQuotaRules().collect { _quotaRules.value = it }
        }
    }

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            settingsRepository.saveSettings(settings)
        }
    }

    fun addQuotaRule(rule: QuotaRule) {
        viewModelScope.launch {
            settingsRepository.saveQuotaRule(rule)
        }
    }

    fun deleteQuotaRule(rule: QuotaRule) {
        viewModelScope.launch {
            settingsRepository.deleteQuotaRule(rule)
        }
    }
}
