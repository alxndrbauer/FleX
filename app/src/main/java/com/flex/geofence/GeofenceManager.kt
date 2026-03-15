package com.flex.geofence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context
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
    fun registerGeofence(lat: Double, lon: Double, radiusMeters: Float) {
        val geofence = Geofence.Builder()
            .setRequestId("office")
            .setCircularRegion(lat, lon, radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
            )
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        client.removeGeofences(listOf("office")).addOnCompleteListener {
            client.addGeofences(request, pendingIntent)
        }
    }

    fun removeGeofence() {
        client.removeGeofences(pendingIntent)
    }
}
