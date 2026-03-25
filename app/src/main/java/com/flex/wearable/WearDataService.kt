package com.flex.wearable

import android.content.Intent
import com.flex.domain.usecase.AutoClockOutUseCase
import com.flex.domain.usecase.WearClockInUseCase
import com.flex.notification.WorkTimerService
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

    @Inject lateinit var wearClockIn: WearClockInUseCase
    @Inject lateinit var autoClockOut: AutoClockOutUseCase
    @Inject lateinit var wearSyncHelper: WearSyncHelper

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onMessageReceived(event: MessageEvent) {
        scope.launch {
            when (event.path) {
                WearContract.MSG_CLOCK_IN -> {
                    wearClockIn()
                    startForegroundService(Intent(this@WearDataService, WorkTimerService::class.java))
                }
                WearContract.MSG_CLOCK_OUT -> {
                    autoClockOut()
                    stopService(Intent(this@WearDataService, WorkTimerService::class.java))
                }
            }
            wearSyncHelper.push() // always push fresh status after any message
        }
    }
}
