package com.vrema.domain.usecase

import com.vrema.domain.model.Settings
import com.vrema.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    operator fun invoke(): Flow<Settings> {
        return repository.getSettings()
    }
}
