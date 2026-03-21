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
    val clockStartMinutes: Int = -1
) {
    val todayFormatted: String
        get() {
            val h = todayMinutes / 60
            val m = todayMinutes % 60
            return "%d:%02d".format(h, m)
        }

    val officePctFormatted: String
        get() = "%.1f%%".format(officePercent)
}

fun DataItem.toWearStatus(): WearStatus {
    val map = DataMapItem.fromDataItem(this).dataMap
    return WearStatus(
        isClockRunning = map.getBoolean(WearContract.KEY_IS_RUNNING),
        todayMinutes = map.getLong(WearContract.KEY_TODAY_MIN),
        officePercent = map.getDouble(WearContract.KEY_OFFICE_PCT),
        officeDays = map.getInt(WearContract.KEY_OFFICE_DAYS),
        quotaMet = map.getBoolean(WearContract.KEY_QUOTA_MET),
        clockStartMinutes = map.getInt(WearContract.KEY_CLOCK_START, -1)
    )
}

fun DataItem.matchesFlexPath(): Boolean =
    uri.path == WearContract.DATA_PATH
