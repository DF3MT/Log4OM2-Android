package com.log4om.android.data.adif

import com.log4om.android.data.model.Qso
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object AdifMapper {

    private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE

    /** Returns null if the record lacks the minimum required fields (CALL, QSO_DATE, BAND, MODE). */
    fun toQso(rec: Map<String, String>, qsoid: Long): Qso? {
        val callsign = rec["CALL"]?.trim()?.uppercase().orEmpty()
        val band     = rec["BAND"]?.trim()?.lowercase().orEmpty()
        val mode     = rec["MODE"]?.trim()?.uppercase().orEmpty()
        val dateStr  = rec["QSO_DATE"]
        if (callsign.isBlank() || band.isBlank() || mode.isBlank() || dateStr.isNullOrBlank()) return null
        val qsoDate  = parseDateTime(dateStr, rec["TIME_ON"]) ?: return null

        return Qso(
            qsoid           = qsoid,
            callsign        = callsign,
            band            = band,
            mode            = mode,
            qsodate         = qsoDate,
            freq            = mhzToKhz(rec["FREQ"]),
            freqrx          = mhzToKhz(rec["FREQ_RX"]),
            rstsent         = rec["RST_SENT"].orEmpty(),
            rstrcvd         = rec["RST_RCVD"].orEmpty(),
            name            = rec["NAME"].orEmpty(),
            qth             = rec["QTH"].orEmpty(),
            country         = resolveCountry(rec, callsign),
            dxcc            = rec["DXCC"]?.toIntOrNull() ?: 0,
            cqzone          = rec["CQZ"]?.toIntOrNull(),
            ituzone         = rec["ITUZ"]?.toIntOrNull(),
            gridsquare      = rec["GRIDSQUARE"].orEmpty(),
            cont            = rec["CONT"].orEmpty(),
            comment         = rec["COMMENT"].orEmpty(),
            notes           = rec["NOTES"].orEmpty(),
            stationcallsign = rec["STATION_CALLSIGN"].orEmpty(),
            mygridsquare    = rec["MY_GRIDSQUARE"].orEmpty(),
            myname          = rec["MY_NAME"].orEmpty(),
            myrig           = rec["MY_RIG"].orEmpty(),
            operator        = rec["OPERATOR"].orEmpty(),
            txpwr           = rec["TX_PWR"]?.toDoubleOrNull(),
            propmode        = rec["PROP_MODE"].orEmpty(),
            contestid       = rec["CONTEST_ID"].orEmpty(),
            address         = rec["ADDRESS"].orEmpty(),
            email           = rec["EMAIL"].orEmpty(),
            pfx             = rec["PFX"].orEmpty(),
            state           = rec["STATE"].orEmpty(),
            cnty            = rec["CNTY"].orEmpty(),
            qslvia          = rec["QSL_VIA"].orEmpty(),
            qslmsg          = rec["QSL_MSG"].orEmpty(),
            satname         = rec["SAT_NAME"].orEmpty(),
            satmode         = rec["SAT_MODE"].orEmpty(),
            satelliteqso    = if (rec["SAT_NAME"]?.isNotBlank() == true) 1 else 0,
            classField      = rec["CLASS"].orEmpty(),
            srxstring       = rec["SRX_STRING"].orEmpty(),
            stxstring       = rec["STX_STRING"].orEmpty(),
            sotaRef         = rec["SOTA_REF"].orEmpty(),
            iota            = rec["IOTA"].orEmpty(),
            potaRef         = rec["POTA_REF"].orEmpty(),
            wwffRef         = rec["WWFF_REF"].orEmpty(),
            cotaRef         = rec["COTA_REF"].orEmpty(),
            lat             = rec["LAT"]?.toDoubleOrNull(),
            lon             = rec["LON"]?.toDoubleOrNull(),
            distance        = rec["DISTANCE"]?.toDoubleOrNull(),
            programid       = rec["PROGRAMID"].ifBlankOrNull("Log4OM Android"),
            programversion  = rec["PROGRAMVERSION"].ifBlankOrNull("1.0")
        )
    }

    private fun parseDateTime(date: String, time: String?): LocalDateTime? {
        val d = runCatching { LocalDate.parse(date.trim(), DATE_FMT) }.getOrNull() ?: return null
        val t = parseTime(time)
        return LocalDateTime.of(d, t)
    }

    private fun parseTime(s: String?): LocalTime {
        if (s.isNullOrBlank()) return LocalTime.MIDNIGHT
        return runCatching {
            val v = s.trim()
            val padded = if (v.length <= 4) v.padEnd(4, '0') else v.padEnd(6, '0')
            when (padded.length) {
                4 -> LocalTime.of(padded.substring(0, 2).toInt(), padded.substring(2, 4).toInt())
                else -> LocalTime.of(
                    padded.substring(0, 2).toInt(),
                    padded.substring(2, 4).toInt(),
                    padded.substring(4, 6).toInt()
                )
            }
        }.getOrDefault(LocalTime.MIDNIGHT)
    }

    private fun String?.ifBlankOrNull(default: String): String =
        if (this.isNullOrBlank()) default else this

    private fun mhzToKhz(v: String?): Double =
        (v?.toDoubleOrNull() ?: 0.0) * 1000.0

    private fun resolveCountry(rec: Map<String, String>, callsign: String): String =
        rec["COUNTRY"]?.takeIf { it.isNotBlank() }
            ?: rec["COUNTRY_INTL"]?.takeIf { it.isNotBlank() }
            ?: CallsignCountry.fromCallsign(callsign).orEmpty()
}
