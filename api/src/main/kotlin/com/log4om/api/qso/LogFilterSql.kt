package com.log4om.api.qso

import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.format.DateTimeFormatter

internal object LogFilterSql {
    private val TS_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    data class Built(
        val whereSql: String,
        val binders: List<(PreparedStatement, Int) -> Int>
    ) {
        fun bind(stmt: PreparedStatement, startIndex: Int = 1): Int {
            var idx = startIndex
            binders.forEach { binder -> idx = binder(stmt, idx) }
            return idx
        }
    }

    fun build(filter: LogFilterDto): Built {
        val parts = mutableListOf<String>()
        val binders = mutableListOf<(PreparedStatement, Int) -> Int>()

        if (filter.callsign.isNotBlank()) {
            parts += "callsign LIKE ? ESCAPE '!'"
            val pattern = toLikePattern(filter.callsign.trim().uppercase())
            binders += { stmt, i -> stmt.setString(i, pattern); i + 1 }
        }
        if (filter.band.isNotBlank()) {
            parts += "band = ?"
            val band = filter.band.trim()
            binders += { stmt, i -> stmt.setString(i, band); i + 1 }
        }
        if (filter.mode.isNotBlank()) {
            parts += "mode = ?"
            val mode = filter.mode.trim()
            binders += { stmt, i -> stmt.setString(i, mode); i + 1 }
        }
        filter.dateFrom?.let { from ->
            parts += "qsodate >= ?"
            val ts = Timestamp.valueOf(from.atStartOfDay().format(TS_FMT))
            binders += { stmt, i -> stmt.setTimestamp(i, ts); i + 1 }
        }
        filter.dateTo?.let { to ->
            parts += "qsodate < ?"
            val ts = Timestamp.valueOf(to.plusDays(1).atStartOfDay().format(TS_FMT))
            binders += { stmt, i -> stmt.setTimestamp(i, ts); i + 1 }
        }
        if (filter.country.isNotBlank()) {
            parts += "country LIKE ? ESCAPE '!'"
            val pattern = toLikePattern(filter.country.trim())
            binders += { stmt, i -> stmt.setString(i, pattern); i + 1 }
        }
        filter.dxcc.trim().toIntOrNull()?.takeIf { it > 0 }?.let { dxcc ->
            parts += "dxcc = ?"
            binders += { stmt, i -> stmt.setInt(i, dxcc); i + 1 }
        }
        fun award(ref: String, award: String) {
            if (ref.isNotBlank()) {
                parts += "CAST(contactreferences AS CHAR) LIKE ? ESCAPE '!'"
                val pattern = "%$award" + toLikePattern(ref.trim().uppercase())
                binders += { stmt, i -> stmt.setString(i, pattern); i + 1 }
            }
        }
        award(filter.sotaRef, "SOTA")
        award(filter.iota, "IOTA")
        award(filter.potaRef, "POTA")
        award(filter.wwffRef, "WWFF")
        award(filter.cotaRef, "COTA")

        val where = if (parts.isEmpty()) "" else "WHERE " + parts.joinToString(" AND ")
        return Built(where, binders)
    }

    fun toLikePattern(raw: String): String {
        val escaped = buildString(raw.length * 2) {
            for (c in raw) {
                when (c) {
                    '!' -> append("!!")
                    '%' -> append("!%")
                    '_' -> append("!_")
                    else -> append(c)
                }
            }
        }
        return if (escaped.contains('*')) escaped.replace("*", "%") else "%$escaped%"
    }
}
