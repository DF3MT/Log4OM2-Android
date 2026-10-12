package com.log4om.api.sync

import com.log4om.api.qso.QsoDto
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Identity of one contact for two-way sync.
 * Call, UTC date, hour+minute, band and mode must match.
 * Seconds match when both sides have them; a HHMM-only ADIF time matches any second in that minute.
 */
data class QsoMatchKey(
    val call: String,
    val day: String,
    val hhmm: String,
    val second: Int?,
    val band: String,
    val mode: String
) {
    val bucket: String get() = "$call|$day|$hhmm|$band|$mode"

    fun conflicts(other: QsoMatchKey): Boolean {
        if (bucket != other.bucket) return false
        val a = second
        val b = other.second
        if (a == null || b == null) return true
        return a == b
    }
}

object QsoIdentity {
    private val dayFmt = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val hhmmFmt = DateTimeFormatter.ofPattern("HHmm")

    fun key(qso: QsoDto): QsoMatchKey = key(
        call = qso.callsign,
        whenUtc = qso.qsodate,
        band = qso.band,
        mode = qso.mode,
        secondsExact = qso.matchSecondsExact
    )

    fun key(
        call: String,
        whenUtc: LocalDateTime,
        band: String,
        mode: String,
        secondsExact: Boolean
    ): QsoMatchKey = QsoMatchKey(
        call = call.trim().uppercase(),
        day = whenUtc.format(dayFmt),
        hhmm = whenUtc.format(hhmmFmt),
        second = if (secondsExact) whenUtc.second else null,
        band = normalizeBand(band),
        mode = normalizeMode(mode)
    )

    fun normalizeBand(band: String): String = band.trim().lowercase().replace(" ", "")

    fun normalizeMode(mode: String): String = mode.trim().uppercase()
}

data class SyncPlan(
    val toUpload: List<QsoDto>,
    val toDownload: List<QsoDto>
)

object SyncPlanner {
    fun plan(local: List<QsoDto>, remote: List<QsoDto>): SyncPlan {
        val remoteIndex = index(remote)
        val localIndex = index(local)
        return SyncPlan(
            toUpload = local.filter { isNew(QsoIdentity.key(it), remoteIndex) },
            toDownload = remote.filter { isNew(QsoIdentity.key(it), localIndex) }
        )
    }

    private fun index(qsos: List<QsoDto>): Map<String, Set<Int?>> {
        val map = HashMap<String, MutableSet<Int?>>()
        qsos.forEach { qso ->
            val key = QsoIdentity.key(qso)
            map.getOrPut(key.bucket) { HashSet() }.add(key.second)
        }
        return map
    }

    private fun isNew(key: QsoMatchKey, index: Map<String, Set<Int?>>): Boolean {
        val seconds = index[key.bucket] ?: return true
        val mine = key.second ?: return false
        return seconds.none { it == null || it == mine }
    }
}
