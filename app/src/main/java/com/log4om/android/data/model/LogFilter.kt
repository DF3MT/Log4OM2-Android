package com.log4om.android.data.model

import java.time.LocalDate

/**
 * Logbook list filters. Empty / null fields are ignored.
 * [callsign] and [country] support `*` as wildcard (SQL LIKE `%`).
 */
data class LogFilter(
    val callsign: String = "",
    val band: String = "",
    val mode: String = "",
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val country: String = "",
    val dxcc: String = "",
    val sotaRef: String = "",
    val iota: String = "",
    val potaRef: String = "",
    val wwffRef: String = ""
) {
    val isActive: Boolean
        get() = callsign.isNotBlank() ||
            band.isNotBlank() ||
            mode.isNotBlank() ||
            dateFrom != null ||
            dateTo != null ||
            country.isNotBlank() ||
            dxcc.isNotBlank() ||
            sotaRef.isNotBlank() ||
            iota.isNotBlank() ||
            potaRef.isNotBlank() ||
            wwffRef.isNotBlank()

    val dxccNumber: Int? get() = dxcc.trim().toIntOrNull()?.takeIf { it > 0 }
}
