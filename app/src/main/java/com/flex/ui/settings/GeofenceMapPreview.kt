package com.flex.ui.settings

import android.preference.PreferenceManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon

@Composable
fun GeofenceMapPreview(
    lat: Double,
    lon: Double,
    radiusMeters: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()

    key(lat, lon, radiusMeters) {
        val mapView = remember {
            @Suppress("DEPRECATION")
            Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)

                val center = GeoPoint(lat, lon)
                val circle = Polygon(this).apply {
                    points = Polygon.pointsAsCircle(center, radiusMeters.toDouble())
                    fillColor = android.graphics.Color.argb(
                        40,
                        android.graphics.Color.red(primaryColor),
                        android.graphics.Color.green(primaryColor),
                        android.graphics.Color.blue(primaryColor)
                    )
                    strokeColor = android.graphics.Color.argb(
                        200,
                        android.graphics.Color.red(primaryColor),
                        android.graphics.Color.green(primaryColor),
                        android.graphics.Color.blue(primaryColor)
                    )
                    strokeWidth = 4f
                }
                overlays.add(circle)
                controller.setZoom(17.0)
                controller.setCenter(center)
            }
        }

        AndroidView(
            factory = { mapView },
            modifier = modifier
                .fillMaxWidth()
                .height(220.dp)
        )
    }
}
