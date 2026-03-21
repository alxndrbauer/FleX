package com.flex.wearable

import com.flex.domain.usecase.AutoClockInUseCase
import com.flex.domain.usecase.AutoClockOutUseCase
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WearDataService : WearableListenerService() {

    @Inject lateinit var autoClockIn: AutoClockInUseCase
    @Inject lateinit var autoClockOut: AutoClockOutUseCase
    @Inject lateinit var wearSyncHelper: WearSyncHelper

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onMessageReceived(event: MessageEvent) {
        scope.launch {
            when (event.path) {
                WearContract.MSG_CLOCK_IN -> autoClockIn()
                WearContract.MSG_CLOCK_OUT -> autoClockOut()
            }
            wearSyncHelper.push()
        }
    }
}
