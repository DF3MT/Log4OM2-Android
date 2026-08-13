package com.log4om.android.data.network

import com.log4om.android.data.model.LogFilter
import com.log4om.android.data.model.Qso
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object QsoJson {
    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun toJson(qso: Qso): JSONObject = JSONObject().apply {
        putOpt("qsoid", qso.qsoid.takeIf { it > 0 })
        put("callsign", qso.callsign)
        put("band", qso.band)
        put("mode", qso.mode)
        put("qsodate", qso.qsodate.format(fmt))
        put("freq", qso.freq)
        put("freqrx", qso.freqrx)
        put("rstsent", qso.rstsent)
        put("rstrcvd", qso.rstrcvd)
        put("name", qso.name)
        put("address", qso.address)
        put("qth", qso.qth)
        put("country", qso.country)
        put("dxcc", qso.dxcc)
        putOpt("cqzone", qso.cqzone)
        putOpt("ituzone", qso.ituzone)
        put("gridsquare", qso.gridsquare)
        put("cont", qso.cont)
        put("comment", qso.comment)
        put("notes", qso.notes)
        putOpt("txpwr", qso.txpwr)
        put("propmode", qso.propmode)
        put("contestid", qso.contestid)
        put("satmode", qso.satmode)
        put("satname", qso.satname)
        put("satelliteqso", qso.satelliteqso)
        put("stationcallsign", qso.stationcallsign)
        put("mygridsquare", qso.mygridsquare)
        put("myname", qso.myname)
        put("myrig", qso.myrig)
        put("mycountry", qso.mycountry)
        putOpt("mydxcc", qso.mydxcc)
        putOpt("mylat", qso.mylat)
        putOpt("mylon", qso.mylon)
        put("operator", qso.operator)
        put("bandrx", qso.bandrx)
        putOpt("lat", qso.lat)
        putOpt("lon", qso.lon)
        putOpt("distance", qso.distance)
        put("sotaRef", qso.sotaRef)
        put("iota", qso.iota)
        put("potaRef", qso.potaRef)
        put("wwffRef", qso.wwffRef)
        put("cotaRef", qso.cotaRef)
        put("programid", qso.programid)
        put("programversion", qso.programversion)
    }

    fun fromJson(o: JSONObject): Qso = Qso(
        qsoid = o.optLong("qsoid", 0L),
        callsign = o.optString("callsign"),
        band = o.optString("band"),
        mode = o.optString("mode"),
        qsodate = parseDate(o.optString("qsodate")),
        freq = o.optDouble("freq", 0.0),
        freqrx = o.optDouble("freqrx", 0.0),
        rstsent = o.optString("rstsent", "59"),
        rstrcvd = o.optString("rstrcvd", "59"),
        name = o.optString("name"),
        address = o.optString("address"),
        qth = o.optString("qth"),
        country = o.optString("country"),
        dxcc = o.optInt("dxcc", 0),
        cqzone = o.optIntOrNull("cqzone"),
        ituzone = o.optIntOrNull("ituzone"),
        gridsquare = o.optString("gridsquare"),
        cont = o.optString("cont"),
        comment = o.optString("comment"),
        notes = o.optString("notes"),
        txpwr = o.optDoubleOrNull("txpwr"),
        propmode = o.optString("propmode"),
        contestid = o.optString("contestid"),
        satmode = o.optString("satmode"),
        satname = o.optString("satname"),
        satelliteqso = o.optInt("satelliteqso", 0),
        stationcallsign = o.optString("stationcallsign"),
        mygridsquare = o.optString("mygridsquare"),
        myname = o.optString("myname"),
        myrig = o.optString("myrig"),
        mycountry = o.optString("mycountry"),
        mydxcc = o.optIntOrNull("mydxcc"),
        mylat = o.optDoubleOrNull("mylat"),
        mylon = o.optDoubleOrNull("mylon"),
        operator = o.optString("operator"),
        bandrx = o.optString("bandrx"),
        lat = o.optDoubleOrNull("lat"),
        lon = o.optDoubleOrNull("lon"),
        distance = o.optDoubleOrNull("distance"),
        sotaRef = o.optString("sotaRef"),
        iota = o.optString("iota"),
        potaRef = o.optString("potaRef"),
        wwffRef = o.optString("wwffRef"),
        cotaRef = o.optString("cotaRef"),
        programid = o.optString("programid", "Log4OM Android"),
        programversion = o.optString("programversion", "1.0")
    )

    fun listFromJson(raw: String): List<Qso> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
    }

    fun filterQuery(filter: LogFilter, limit: Int? = null, offset: Int? = null): String {
        val parts = mutableListOf<String>()
        fun add(k: String, v: String?) {
            if (!v.isNullOrBlank()) parts += "$k=${java.net.URLEncoder.encode(v, "UTF-8")}"
        }
        add("callsign", filter.callsign)
        add("band", filter.band)
        add("mode", filter.mode)
        add("dateFrom", filter.dateFrom?.toString())
        add("dateTo", filter.dateTo?.toString())
        add("country", filter.country)
        add("dxcc", filter.dxcc)
        add("sotaRef", filter.sotaRef)
        add("iota", filter.iota)
        add("potaRef", filter.potaRef)
        add("wwffRef", filter.wwffRef)
        add("cotaRef", filter.cotaRef)
        if (limit != null) parts += "limit=$limit"
        if (offset != null) parts += "offset=$offset"
        return if (parts.isEmpty()) "" else "?${parts.joinToString("&")}"
    }

    private fun parseDate(raw: String): LocalDateTime {
        if (raw.isBlank()) return LocalDateTime.now()
        val cleaned = raw.replace(" ", "T").let {
            when {
                it.length == 16 -> "$it:00"
                else -> it
            }
        }
        return runCatching { LocalDateTime.parse(cleaned, fmt) }
            .recoverCatching { LocalDateTime.parse(cleaned.take(19)) }
            .getOrElse { LocalDateTime.now() }
    }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key) else null
}
