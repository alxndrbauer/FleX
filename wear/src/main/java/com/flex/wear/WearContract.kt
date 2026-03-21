package com.flex.wear

object WearContract {
    const val DATA_PATH = "/flex/status"
    const val MSG_CLOCK_IN = "/flex/clockin"
    const val MSG_CLOCK_OUT = "/flex/clockout"

    const val KEY_IS_RUNNING = "isClockRunning"
    const val KEY_TODAY_MIN = "todayMinutes"
    const val KEY_OFFICE_PCT = "officePercent"
    const val KEY_OFFICE_DAYS = "officeDays"
    const val KEY_QUOTA_MET = "quotaMet"
    const val KEY_CLOCK_START = "clockStartMinutes"
    const val KEY_FLEXTIME_MIN = "flextimeMinutes"
    const val KEY_OVERTIME_MIN = "overtimeMinutes"
    const val KEY_REQUIRED_OFFICE_DAYS = "requiredOfficeDays"
    const val MSG_REQUEST_STATUS = "/flex/status_request"
}
