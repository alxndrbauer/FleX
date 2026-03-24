package com.flex.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey
    val id: Long = 1,
    val dailyWorkMinutes: Int = 426,
    val monthlyWorkMinutes: Int = 9252,
    val officeQuotaPercent: Int = 40,
    val officeQuotaMinDays: Int = 8,
    val initialFlextimeMinutes: Int = 0,
    val initialOvertimeMinutes: Int = 0,
    val annualVacationDays: Int = 30,
    val carryOverVacationDays: Int = 0,
    val specialVacationDays: Int = 5,
    val settingsYear: Int = 0,
    val geofenceEnabled: Boolean = false,
    val geofenceLat: Double = 0.0,
    val geofenceLon: Double = 0.0,
    val geofenceRadiusMeters: Float = 150f,
    val geofenceAddress: String = "",
    val wifiAutoStampEnabled: Boolean = false,
    val wifiSsid: String = ""
)
