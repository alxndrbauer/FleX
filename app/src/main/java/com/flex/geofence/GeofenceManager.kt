package com.flex.geofence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.flex.data.local.GeofencePreferences
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeofenceManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val geofencePreferences: GeofencePreferences,
    private val clock: Clock
) {
    private val client = LocationServices.getGeofencingClient(context)

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceReceiver::class.java)
        PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    @SuppressLint("MissingPermission")
    fun registerGeofence(
        lat: Double,
        lon: Double,
        radiusMeters: Float,
        initialTrigger: Int = 0,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val geofence = Geofence.Builder()
            .setRequestId("office")
            .setCircularRegion(lat, lon, radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setLoiteringDelay(60 * 1000) // 1 Minute – verhindert Boundary Flickering
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or
                Geofence.GEOFENCE_TRANSITION_DWELL or
                Geofence.GEOFENCE_TRANSITION_EXIT
            )
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(initialTrigger)
            .addGeofence(geofence)
            .build()

        geofencePreferences.markRegistrationPending()

        // Adding a geofence with an existing request ID replaces it atomically. Removing it
        // first would leave a window without any fence if the process dies in between
        // (app update, swiped away right after launch).
        client.addGeofences(request, pendingIntent)
            .addOnSuccessListener {
                Log.d("GeofenceManager", "Geofence registered: radius=$radiusMeters")
                geofencePreferences.markRegistrationSuccess(clock.millis())
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e("GeofenceManager", "Failed to register geofence: ${e.message}")
                geofencePreferences.markRegistrationFailure(clock.millis())
                onFailure(e)
            }
    }

    fun removeGeofence() {
        client.removeGeofences(pendingIntent)
            .addOnSuccessListener { geofencePreferences.clearRegistration() }
    }
}
