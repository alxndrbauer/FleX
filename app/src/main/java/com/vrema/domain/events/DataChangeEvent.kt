package com.vrema.domain.events

sealed class DataChangeEvent {
    object WorkDayChanged : DataChangeEvent()
    object SettingsChanged : DataChangeEvent()
}
