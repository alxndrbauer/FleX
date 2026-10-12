package com.flex.widget

import com.flex.data.local.PausePreferences
import com.flex.domain.events.DataChangeEventBus
import com.flex.domain.repository.SettingsRepository
import com.flex.domain.usecase.AutoClockOutUseCase
import com.flex.domain.usecase.ClockInUseCase
import com.flex.domain.usecase.PauseWorkUseCase
import com.flex.domain.usecase.SwitchLocationUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FlexWidgetEntryPoint {
    fun flexWidgetDataProvider(): FlexWidgetDataProvider
    fun clockInUseCase(): ClockInUseCase
    fun autoClockOutUseCase(): AutoClockOutUseCase
    fun pauseWorkUseCase(): PauseWorkUseCase
    fun switchLocationUseCase(): SwitchLocationUseCase
    fun settingsRepository(): SettingsRepository
    fun pausePreferences(): PausePreferences
    fun dataChangeEventBus(): DataChangeEventBus
}
