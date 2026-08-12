package com.log4om.android.ui.components

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

/**
 * Embedded OpenStreetMap (osmdroid) showing QSO and optional station markers.
 */
@Composable
fun OsmMiniMap(
    contactLat: Double,
    contactLon: Double,
    stationLat: Double? = null,
    stationLon: Double? = null,
    contactLabel: String = "QSO",
    stationLabel: String = "QTH",
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(220.dp)
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        ensureOsmdroidConfig(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(6.0)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            map.overlays.clear()
            val contact = GeoPoint(contactLat, contactLon)
            map.overlays += Marker(map).apply {
                position = contact
                title = contactLabel
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }

            val station = if (stationLat != null && stationLon != null) {
                GeoPoint(stationLat, stationLon)
            } else null

            if (station != null) {
                map.overlays += Marker(map).apply {
                    position = station
                    title = stationLabel
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                map.overlays += Polyline().apply {
                    setPoints(listOf(station, contact))
                    outlinePaint.strokeWidth = 4f
                }
                val box = BoundingBox.fromGeoPoints(listOf(station, contact))
                map.post {
                    map.zoomToBoundingBox(box.increaseByScale(1.6f), false)
                }
            } else {
                map.controller.setCenter(contact)
                map.controller.setZoom(8.0)
            }
            map.invalidate()
        }
    )
}

private fun ensureOsmdroidConfig(context: Context) {
    val appCtx = context.applicationContext
    val base = File(appCtx.cacheDir, "osmdroid")
    val tiles = File(base, "tiles")
    if (!tiles.exists()) tiles.mkdirs()
    Configuration.getInstance().apply {
        userAgentValue = appCtx.packageName
        osmdroidBasePath = base
        osmdroidTileCache = tiles
    }
}
