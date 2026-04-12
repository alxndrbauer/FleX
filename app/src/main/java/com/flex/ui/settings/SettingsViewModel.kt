package com.flex.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.net.wifi.WifiManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flex.calendar.CalendarInfo
import com.flex.calendar.CalendarSyncService
import com.flex.data.local.AppIconPreferences
import com.flex.data.local.ThemePreferences
import com.flex.data.export.IcsExportService
import com.flex.domain.model.AppIconVariant
import com.flex.domain.model.DayType
import com.flex.domain.model.FederalState
import com.flex.domain.model.QuotaRule
import com.flex.domain.model.Settings
import com.flex.domain.model.ThemeMode
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.repository.WorkDayRepository
import com.flex.domain.usecase.GetSettingsUseCase
import com.flex.geofence.GeofenceManager
import com.flex.wifi.WifiMonitor
import kotlinx.coroutines.flow.firstOrNull
import java.time.LocalDate
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
    @param:ApplicationContext private val context: Context,
    private val getSettings: GetSettingsUseCase,
    private val settingsRepository: SettingsRepository,
    private val themePreferences: ThemePreferences,
    private val appIconPreferences: AppIconPreferences,
    private val geofenceManager: GeofenceManager,
    private val wifiMonitor: WifiMonitor,
    private val calendarSyncService: CalendarSyncService,
    private val workDayRepository: WorkDayRepository,
    private val icsExportService: IcsExportService
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
            getSettings().collect { s ->
                _settings.value = s
                Log.d("GeoDebug", "Settings loaded: enabled=${s.geofenceEnabled}, lat=${s.geofenceLat}, lon=${s.geofenceLon}, status=${_geofenceStatus.value}")
                if (_geofenceStatus.value == GeofenceStatus.UNKNOWN &&
                    s.geofenceEnabled && s.geofenceLat != 0.0 && s.geofenceLon != 0.0
                ) {
                    Log.d("GeoDebug", "Init: setting status → REGISTERED")
                    _geofenceStatus.value = GeofenceStatus.REGISTERED
                }
            }
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

    fun updateFederalState(state: FederalState) {
        viewModelScope.launch {
            settingsRepository.saveSettings(_settings.value.copy(federalState = state))
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

    @SuppressLint("MissingPermission")
    fun getCurrentWifiSsid(): String? {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        return wifiManager.connectionInfo?.ssid
            ?.removeSurrounding("\"")
            ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    }

    fun saveWifiSettings(enabled: Boolean, ssid: String) {
        viewModelScope.launch {
            val trimmedSsid = ssid.trim()
            settingsRepository.saveSettings(
                _settings.value.copy(
                    wifiAutoStampEnabled = enabled,
                    wifiSsid = trimmedSsid
                )
            )
            if (enabled && trimmedSsid.isNotBlank()) {
                wifiMonitor.register(trimmedSsid)
            } else {
                wifiMonitor.unregister()
            }
        }
    }

    fun getAvailableCalendars(): List<CalendarInfo> = calendarSyncService.getAvailableCalendars()

    fun saveCalendarSettings(
        enabled: Boolean,
        calendarId: Long,
        syncTypes: String,
        syncOffice: Boolean,
        syncHomeOffice: Boolean,
        eventPrefix: String,
        noAlarm: Boolean
    ) {
        viewModelScope.launch {
            settingsRepository.saveSettings(
                _settings.value.copy(
                    calendarSyncEnabled = enabled,
                    calendarId = calendarId,
                    calendarSyncTypes = syncTypes,
                    calendarSyncOffice = syncOffice,
                    calendarSyncHomeOffice = syncHomeOffice,
                    calendarEventPrefix = eventPrefix,
                    calendarEventNoAlarm = noAlarm
                )
            )
        }
    }

    fun cleanupOrphanedMappings(onResult: (removed: Int, total: Int) -> Unit) {
        viewModelScope.launch {
            val (removed, total) = calendarSyncService.cleanupOrphanedMappings()
            onResult(removed, total)
        }
    }

    fun syncAllToCalendar(start: LocalDate, end: LocalDate, onResult: (synced: Int, total: Int) -> Unit) {
        viewModelScope.launch {
            val settings = _settings.value
            if (!settings.calendarSyncEnabled || settings.calendarId == -1L) return@launch
            val days = workDayRepository.getWorkDaysInRange(start, end).firstOrNull() ?: return@launch
            val (synced, total) = calendarSyncService.syncAll(days, settings)
            onResult(synced, total)
        }
    }

    fun exportToIcs(
        start: LocalDate,
        end: LocalDate,
        syncTypes: String,
        syncOffice: Boolean,
        syncHomeOffice: Boolean,
        onResult: (String, Int) -> Unit
    ) {
        viewModelScope.launch {
            val settings = _settings.value.copy(
                calendarSyncTypes = syncTypes,
                calendarSyncOffice = syncOffice,
                calendarSyncHomeOffice = syncHomeOffice
            )
            val days = workDayRepository.getWorkDaysInRange(start, end).firstOrNull() ?: emptyList()
            val (content, count) = icsExportService.exportToIcs(days, settings)
            onResult(content, count)
        }
    }

    fun saveGeofenceSettings(enabled: Boolean, lat: Double, lon: Double, radius: Float, address: String = "") {
        Log.d("GeoDebug", "saveGeofenceSettings called: enabled=$enabled, lat=$lat, lon=$lon")
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
                Log.d("GeoDebug", "saveGeofenceSettings: setting status → REGISTERED")
                _geofenceStatus.value = GeofenceStatus.REGISTERED
                geofenceManager.registerGeofence(
                    lat, lon, radius,
                    onFailure = { e ->
                        Log.e("GeoDebug", "registerGeofence FAILED: ${e.message}")
                        _geofenceStatus.value = GeofenceStatus.FAILED
                    }
                )
            } else {
                Log.d("GeoDebug", "saveGeofenceSettings: else branch (lat=$lat, lon=$lon) → UNKNOWN")
                geofenceManager.removeGeofence()
                _geofenceStatus.value = GeofenceStatus.UNKNOWN
            }
        }
    }
}
