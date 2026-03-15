package com.flex.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flex.data.local.ThemePreferences
import com.flex.domain.model.QuotaRule
import com.flex.domain.model.Settings
import com.flex.domain.model.ThemeMode
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.usecase.GetSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getSettings: GetSettingsUseCase,
    private val settingsRepository: SettingsRepository,
    private val themePreferences: ThemePreferences
) : ViewModel() {

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _quotaRules = MutableStateFlow<List<QuotaRule>>(emptyList())
    val quotaRules: StateFlow<List<QuotaRule>> = _quotaRules.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = themePreferences.themeModeFlow

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

    fun setThemeMode(mode: ThemeMode) {
        themePreferences.setThemeMode(mode)
    }
}
