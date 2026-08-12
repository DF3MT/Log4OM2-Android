package com.log4om.android.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Maidenhead locator helpers and great-circle distance / bearing.
 */
object GridLocator {

    data class LatLon(val lat: Double, val lon: Double)

    data class PathInfo(
        val distanceKm: Double,
        val bearingDeg: Double
    )

    fun parse(grid: String): LatLon? {
        val g = grid.trim().uppercase()
        if (g.length < 4) return null
        if (g[0] !in 'A'..'R' || g[1] !in 'A'..'R') return null
        if (g[2] !in '0'..'9' || g[3] !in '0'..'9') return null

        var lon = (g[0] - 'A') * 20.0 - 180.0
        var lat = (g[1] - 'A') * 10.0 - 90.0
        lon += (g[2] - '0') * 2.0
        lat += (g[3] - '0') * 1.0

        if (g.length >= 6 && g[4] in 'A'..'X' && g[5] in 'A'..'X') {
            lon += (g[4] - 'A') * (2.0 / 24.0)
            lat += (g[5] - 'A') * (1.0 / 24.0)
            // center of subsquare
            lon += 2.0 / 24.0 / 2.0
            lat += 1.0 / 24.0 / 2.0
        } else {
            lon += 1.0
            lat += 0.5
        }
        return LatLon(lat, lon)
    }

    fun path(from: LatLon, to: LatLon): PathInfo {
        val r = 6371.0
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)
        val dLat = Math.toRadians(to.lat - from.lat)
        val dLon = Math.toRadians(to.lon - from.lon)

        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val dist = r * c

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        var bearing = Math.toDegrees(atan2(y, x))
        bearing = (bearing + 360.0) % 360.0

        return PathInfo(distanceKm = dist, bearingDeg = bearing)
    }

    fun pathBetweenGrids(myGrid: String, theirGrid: String): PathInfo? {
        val from = parse(myGrid) ?: return null
        val to = parse(theirGrid) ?: return null
        return path(from, to)
    }

    fun formatDistanceKm(km: Double): String =
        if (km >= 100) floor(km + 0.5).toInt().toString() + " km"
        else String.format("%.1f km", km)

    fun formatBearing(deg: Double): String =
        String.format("%.0f°", deg)
}
