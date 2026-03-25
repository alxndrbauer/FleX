package com.flex.wear

import android.net.Uri
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem

data class WearStatus(
    val isClockRunning: Boolean = false,
    val todayMinutes: Long = 0,
    val officePercent: Double = 0.0,
    val officeDays: Int = 0,
    val quotaMet: Boolean = false,
    val clockStartMinutes: Int = -1,
    val flextimeMinutes: Long = 0,
    val overtimeMinutes: Long = 0,
    val requiredOfficeDays: Int = 0
) {
    val todayFormatted: String
        get() {
            val h = todayMinutes / 60
            val m = todayMinutes % 60
            return "%d:%02d".format(h, m)
        }

    val officePctFormatted: String
        get() = "%.1f%%".format(officePercent)

    val flextimeFormatted: String
        get() {
            val abs = kotlin.math.abs(flextimeMinutes)
            val h = abs / 60
            val m = abs % 60
            val sign = if (flextimeMinutes >= 0) "+" else "-"
            return "%s%d:%02d".format(sign, h, m)
        }

    val overtimeFormatted: String
        get() {
            val abs = kotlin.math.abs(overtimeMinutes)
            val h = abs / 60
            val m = abs % 60
            val sign = if (overtimeMinutes >= 0) "+" else "-"
            return "%s%d:%02d".format(sign, h, m)
        }
}

fun DataItem.toWearStatus(): WearStatus {
    val map = DataMapItem.fromDataItem(this).dataMap
    return WearStatus(
        isClockRunning = map.getBoolean(WearContract.KEY_IS_RUNNING),
        todayMinutes = map.getLong(WearContract.KEY_TODAY_MIN),
        officePercent = map.getDouble(WearContract.KEY_OFFICE_PCT),
        officeDays = map.getInt(WearContract.KEY_OFFICE_DAYS),
        quotaMet = map.getBoolean(WearContract.KEY_QUOTA_MET),
        clockStartMinutes = map.getInt(WearContract.KEY_CLOCK_START, -1),
        flextimeMinutes = map.getLong(WearContract.KEY_FLEXTIME_MIN),
        overtimeMinutes = map.getLong(WearContract.KEY_OVERTIME_MIN),
        requiredOfficeDays = map.getInt(WearContract.KEY_REQUIRED_OFFICE_DAYS)
    )
}

fun DataItem.matchesFlexPath(): Boolean =
    uri.path == WearContract.DATA_PATH
