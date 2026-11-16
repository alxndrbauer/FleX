package com.flex.wifi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiConnectionInfo @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun currentSsid(): String? {
        if (!hasLocationPermission()) return null
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return capabilities?.let { ssidFrom(it) } ?: legacySsid()
    }

    @SuppressLint("MissingPermission")
    fun ssidFrom(capabilities: NetworkCapabilities): String? {
        val wifiInfo = capabilities.transportInfo as? WifiInfo ?: return legacySsid()
        return wifiInfo.ssid
            ?.removeSurrounding("\"")
            ?.takeIf { it.isNotBlank() && it != WifiManager.UNKNOWN_SSID }
            ?: legacySsid()
    }

    @Suppress("DEPRECATION")
    private fun legacySsid(): String? =
        wifiManager.connectionInfo?.ssid
            ?.removeSurrounding("\"")
            ?.takeIf { it.isNotBlank() && it != WifiManager.UNKNOWN_SSID }
}
