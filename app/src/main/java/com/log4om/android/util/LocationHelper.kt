package com.log4om.android.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Thin wrapper around [LocationManager]. Tries last-known fix first, falls back to a single
 * active GPS/Network update with timeout. Returns null silently if permission isn't granted or
 * location services are off — callers should treat null as "no GPS data".
 */
class LocationHelper(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun lm(): LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    fun lastKnownLocation(): Location? {
        if (!hasPermission()) return null
        val lm = lm() ?: return null
        var best: Location? = null
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
            if (!lm.isProviderEnabled(p)) continue
            try {
                val loc = lm.getLastKnownLocation(p) ?: continue
                if (best == null || loc.time > best.time) best = loc
            } catch (_: SecurityException) { }
        }
        return best
    }

    /**
     * Returns a fresh location: last known if newer than [maxAgeMs], else a single active fix
     * (waits up to [timeoutMs]), else the last known regardless of age, else null.
     */
    suspend fun currentLocation(maxAgeMs: Long = 60_000L, timeoutMs: Long = 4_000L): Location? {
        if (!hasPermission()) return null
        val lm = lm() ?: return null
        val last = lastKnownLocation()
        if (last != null && System.currentTimeMillis() - last.time < maxAgeMs) return last

        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return last
        }

        val fresh = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        lm.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }
                    override fun onProviderDisabled(provider: String) {}
                    override fun onProviderEnabled(provider: String) {}
                    @Deprecated("Required by old API")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                }
                try {
                    lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                } catch (_: SecurityException) {
                    if (cont.isActive) cont.resume(null)
                }
                cont.invokeOnCancellation {
                    try { lm.removeUpdates(listener) } catch (_: Throwable) {}
                }
            }
        }
        return fresh ?: last
    }
}
