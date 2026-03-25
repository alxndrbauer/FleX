package com.flex.domain.events

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class DataChangeEventBus {
    private val _events = MutableSharedFlow<DataChangeEvent>(replay = 0, extraBufferCapacity = 1)
    val events: SharedFlow<DataChangeEvent> = _events.asSharedFlow()

    suspend fun emit(event: DataChangeEvent) {
        _events.emit(event)
    }
}
