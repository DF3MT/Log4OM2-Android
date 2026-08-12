package com.log4om.android.data.refs

import com.log4om.android.data.prefs.AppPrefs
import com.log4om.android.util.LocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class ActivityProximityService(
    private val catalog: ReferenceCatalog,
    private val prefs: AppPrefs,
    private val locationHelper: LocationHelper
) {

    suspend fun checkHere(): Result<ProximityResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (!locationHelper.hasPermission()) {
                error("NO_LOCATION_PERMISSION")
            }
            val loc = locationHelper.currentLocation(maxAgeMs = 30_000L, timeoutMs = 8_000L)
                ?: error("NO_GPS_FIX")
            val radii = ActivityRadii(
                sotaM = prefs.radiusSotaM.first(),
                potaM = prefs.radiusPotaM.first(),
                wwffM = prefs.radiusWwffM.first(),
                cotaM = prefs.radiusCotaM.first(),
                iotaM = prefs.radiusIotaM.first()
            )
            val refs = catalog.loadAll()
            val hits = ActivityProximityMatcher.match(loc.latitude, loc.longitude, refs, radii)
            ProximityResult(
                lat = loc.latitude,
                lon = loc.longitude,
                hits = hits,
                catalogEmpty = refs.isEmpty()
            )
        }
    }

    fun applyHitsToFields(selected: List<ActivityHit>): Map<ActivityProgram, String> {
        val out = linkedMapOf<ActivityProgram, String>()
        for (hit in selected) {
            val existing = out[hit.ref.program]
            out[hit.ref.program] = if (existing.isNullOrBlank()) {
                hit.ref.reference
            } else if (existing.split(',').any { it.trim().equals(hit.ref.reference, true) }) {
                existing
            } else {
                "$existing,${hit.ref.reference}"
            }
        }
        return out
    }
}
