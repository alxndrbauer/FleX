package com.flex.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flex.data.local.AppIconPreferences
import com.flex.data.local.ThemePreferences
import com.flex.domain.model.AppIconVariant
import com.flex.domain.model.QuotaRule
import com.flex.domain.model.Settings
import com.flex.domain.model.ThemeMode
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.usecase.GetSettingsUseCase
import com.flex.geofence.GeofenceManager
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

enum class GeofenceStatus { UNKNOWN, REGISTERED, FAILED }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getSettings: GetSettingsUseCase,
    private val settingsRepository: SettingsRepository,
    private val themePreferences: ThemePreferences,
    private val appIconPreferences: AppIconPreferences,
    private val geofenceManager: GeofenceManager
) : ViewModel() {

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _quotaRules = MutableStateFlow<List<QuotaRule>>(emptyList())
    val quotaRules: StateFlow<List<QuotaRule>> = _quotaRules.asStateFlow()

    private val _geofenceStatus = MutableStateFlow(GeofenceStatus.UNKNOWN)
    val geofenceStatus: StateFlow<GeofenceStatus> = _geofenceStatus.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = themePreferences.themeModeFlow
    val appIconVariant: StateFlow<AppIconVariant> = appIconPreferences.variantFlow

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

    fun setAppIconVariant(variant: AppIconVariant) {
        appIconPreferences.setVariant(variant)
    }

    // Returns Pair(lat, lon) or null if address not found
    fun geocodeAddress(address: String, onResult: (Double, Double) -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            try {
                @Suppress("DEPRECATION")
                val results = Geocoder(context, Locale.getDefault()).getFromLocationName(address, 1)
                val loc = results?.firstOrNull()
                if (loc != null) onResult(loc.latitude, loc.longitude) else onError()
            } catch (_: Exception) {
                onError()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun fetchCurrentLocation(onResult: (Double, Double) -> Unit, onError: () -> Unit) {
        try {
            LocationServices.getFusedLocationProviderClient(context).lastLocation
                .addOnSuccessListener { loc ->
                    if (loc != null) onResult(loc.latitude, loc.longitude) else onError()
                }
                .addOnFailureListener { onError() }
        } catch (_: Exception) {
            onError()
        }
    }

    fun saveGeofenceSettings(enabled: Boolean, lat: Double, lon: Double, radius: Float, address: String = "") {
        viewModelScope.launch {
            settingsRepository.saveSettings(
                _settings.value.copy(
                    geofenceEnabled = enabled,
                    geofenceLat = lat,
                    geofenceLon = lon,
                    geofenceRadiusMeters = radius,
                    geofenceAddress = address
                )
            )
            if (enabled && lat != 0.0 && lon != 0.0) {
                geofenceManager.registerGeofence(
                    lat, lon, radius,
                    onSuccess = { _geofenceStatus.value = GeofenceStatus.REGISTERED },
                    onFailure = { _geofenceStatus.value = GeofenceStatus.FAILED }
                )
            } else {
                geofenceManager.removeGeofence()
                _geofenceStatus.value = GeofenceStatus.UNKNOWN
            }
        }
    }
}
