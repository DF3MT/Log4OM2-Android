package com.log4om.android.data.refs

import com.log4om.android.util.GridLocator
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * Grid-bucket candidate filter (~0.2°) then precise radius / bbox test.
 */
object ActivityProximityMatcher {

    private const val CELL = 0.2

    fun match(
        lat: Double,
        lon: Double,
        refs: List<ActivityRef>,
        radii: ActivityRadii
    ): List<ActivityHit> {
        if (refs.isEmpty()) return emptyList()
        val index = buildIndex(refs)
        val maxRadiusM = max(
            max(radii.sotaM, radii.potaM),
            max(radii.wwffM, max(radii.cotaM, radii.iotaM))
        )
        val padCells = ceil((maxRadiusM / 111_000.0) / CELL).toInt().coerceAtLeast(1)
        val candidates = linkedSetOf<ActivityRef>()
        val lat0 = floor(lat / CELL).toInt()
        val lon0 = floor(lon / CELL).toInt()
        for (dLat in -padCells..padCells) {
            for (dLon in -padCells..padCells) {
                index[cellKey(lat0 + dLat, lon0 + dLon)]?.let { candidates.addAll(it) }
            }
        }
        // Also include bbox-bearing refs that may span multiple cells
        refs.asSequence()
            .filter { it.bbox != null && it.bbox.size == 4 }
            .forEach { candidates += it }

        val hits = ArrayList<ActivityHit>()
        for (ref in candidates) {
            matchOne(lat, lon, ref, radii)?.let { hits += it }
        }
        return hits.sortedWith(
            compareBy<ActivityHit> { it.ref.program.ordinal }
                .thenBy { it.distanceM }
                .thenBy { it.ref.reference }
        )
    }

    fun matchOne(
        lat: Double,
        lon: Double,
        ref: ActivityRef,
        radii: ActivityRadii
    ): ActivityHit? {
        val radius = radii.forProgram(ref.program)
        val bbox = ref.bbox
        if (bbox != null && bbox.size == 4) {
            val south = bbox[0]
            val west = bbox[1]
            val north = bbox[2]
            val east = bbox[3]
            if (lat in south..north && lon in west..east) {
                val distM = distanceM(lat, lon, ref.lat, ref.lon)
                return ActivityHit(ref, distM, MatchMethod.BBOX, radius)
            }
            // Fall through to radius around center if outside bbox
        }
        val distM = distanceM(lat, lon, ref.lat, ref.lon)
        if (distM <= radius) {
            return ActivityHit(ref, distM, MatchMethod.RADIUS, radius)
        }
        return null
    }

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
        GridLocator.path(
            GridLocator.LatLon(lat1, lon1),
            GridLocator.LatLon(lat2, lon2)
        ).distanceKm * 1000.0

    private fun buildIndex(refs: List<ActivityRef>): Map<Long, List<ActivityRef>> {
        val map = HashMap<Long, MutableList<ActivityRef>>()
        for (ref in refs) {
            val key = cellKey(floor(ref.lat / CELL).toInt(), floor(ref.lon / CELL).toInt())
            map.getOrPut(key) { mutableListOf() }.add(ref)
        }
        return map
    }

    private fun cellKey(latCell: Int, lonCell: Int): Long =
        (latCell.toLong() shl 32) xor (lonCell.toLong() and 0xffffffffL)
}
