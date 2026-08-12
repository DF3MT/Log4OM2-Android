package com.log4om.android.data.refs

enum class ActivityProgram {
    SOTA,
    POTA,
    WWFF,
    COTA,
    IOTA
}

enum class MatchMethod {
    RADIUS,
    BBOX,
    POLYGON
}

data class ActivityRef(
    val program: ActivityProgram,
    val reference: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val country: String = "",
    /** Optional south, west, north, east for IOTA-style bbox matches. */
    val bbox: DoubleArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ActivityRef) return false
        return program == other.program &&
            reference == other.reference &&
            name == other.name &&
            lat == other.lat &&
            lon == other.lon &&
            country == other.country &&
            bbox.contentEquals(other.bbox)
    }

    override fun hashCode(): Int {
        var result = program.hashCode()
        result = 31 * result + reference.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + lat.hashCode()
        result = 31 * result + lon.hashCode()
        result = 31 * result + country.hashCode()
        result = 31 * result + (bbox?.contentHashCode() ?: 0)
        return result
    }
}

data class ActivityHit(
    val ref: ActivityRef,
    val distanceM: Double,
    val method: MatchMethod,
    val radiusM: Int
)

data class ActivityRadii(
    val sotaM: Int = 200,
    val potaM: Int = 800,
    val wwffM: Int = 500,
    val cotaM: Int = 1000,
    val iotaM: Int = 5000
) {
    fun forProgram(program: ActivityProgram): Int = when (program) {
        ActivityProgram.SOTA -> sotaM
        ActivityProgram.POTA -> potaM
        ActivityProgram.WWFF -> wwffM
        ActivityProgram.COTA -> cotaM
        ActivityProgram.IOTA -> iotaM
    }
}

data class ProximityResult(
    val lat: Double,
    val lon: Double,
    val hits: List<ActivityHit>,
    val catalogEmpty: Boolean
)

data class SyncProgress(
    val program: ActivityProgram,
    val message: String,
    val count: Int = 0,
    val done: Boolean = false
)

data class SyncReport(
    val counts: Map<ActivityProgram, Int>,
    val notes: List<String>
)
