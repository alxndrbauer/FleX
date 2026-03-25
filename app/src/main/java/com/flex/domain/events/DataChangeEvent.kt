package com.flex.domain.events

sealed class DataChangeEvent {
    object WorkDayChanged : DataChangeEvent()
    object SettingsChanged : DataChangeEvent()
}
