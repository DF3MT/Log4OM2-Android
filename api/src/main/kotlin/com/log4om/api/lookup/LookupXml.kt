package com.log4om.api.lookup

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate

/**
 * QRZ XML, HamQTH XML, and Club Log DXCC parsing.
 * Same endpoints and query shape as the Android clients in
 * QrzApiService / HamQthApiService / ClubLogApiService.
 */
internal object LookupXml {
    private val UTF8 = StandardCharsets.UTF_8

    fun qrzLoginUrl(user: String, password: String): String {
        val u = enc(user)
        val p = enc(password)
        return "https://xmldata.qrz.com/xml/current/?username=$u&password=$p&agent=Log4OM-Android-1.0"
    }

    fun qrzLookupUrl(sessionKey: String, callsign: String): String {
        val s = enc(sessionKey)
        val c = enc(callsign.uppercase())
        return "https://xmldata.qrz.com/xml/current/?s=$s&callsign=$c"
    }

    fun hamqthLoginUrl(user: String, password: String): String {
        return "https://www.hamqth.com/xml.php?u=${enc(user)}&p=${enc(password)}"
    }

    fun hamqthLookupUrl(sessionId: String, callsign: String): String {
        val id = enc(sessionId)
        val call = enc(callsign.uppercase())
        return "https://www.hamqth.com/xml.php?id=$id&callsign=$call&prg=Log4OMAndroid"
    }

    fun clubLogUrl(apiKey: String, callsign: String, date: LocalDate): String {
        val call = enc(callsign.uppercase())
        val key = enc(apiKey)
        return "https://clublog.org/dxcc?call=$call&api=$key" +
            "&year=${date.year}&month=${date.monthValue}&day=${date.dayOfMonth}&full=1"
    }

    fun tag(xml: String, name: String): String? {
        val re = Regex(
            """<(?:[\w.-]+:)?${Regex.escape(name)}(?:\s[^>]*)?>([\s\S]*?)</(?:[\w.-]+:)?${Regex.escape(name)}>""",
            RegexOption.IGNORE_CASE
        )
        return re.find(xml)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let(::unescape)
    }

    data class QrzCall(
        val call: String = "",
        val name: String = "",
        val country: String = "",
        val grid: String = "",
        val error: String? = null
    )

    fun parseQrzCallsign(xml: String): QrzCall {
        val block = tag(xml, "Callsign")
        if (block == null) {
            return QrzCall(error = tag(xml, "Error") ?: tag(xml, "Remark") ?: "QRZ lookup failed")
        }
        fun f(name: String) = tag(block, name).orEmpty()
        val fullName = listOf(f("fname"), f("name")).filter { it.isNotBlank() }.joinToString(" ")
        return QrzCall(
            call = f("call"),
            name = fullName,
            country = f("country"),
            grid = f("grid")
        )
    }

    data class HamQthCall(
        val call: String = "",
        val name: String = "",
        val country: String = "",
        val grid: String = "",
        val error: String? = null
    )

    fun parseHamQth(xml: String): HamQthCall {
        val block = tag(xml, "search")
        if (block == null) {
            return HamQthCall(error = tag(xml, "error") ?: "HamQTH lookup failed")
        }
        fun f(name: String) = tag(block, name).orEmpty()
        val name = f("adr_name").ifBlank { f("nick") }
        if (f("callsign").isBlank() && name.isBlank() && f("country").isBlank() && f("grid").isBlank()) {
            return HamQthCall(error = tag(xml, "error") ?: "HamQTH lookup failed")
        }
        return HamQthCall(
            call = f("callsign"),
            name = name,
            country = f("country").ifBlank { f("adr_country") },
            grid = f("grid").uppercase()
        )
    }

    private fun enc(value: String): String = URLEncoder.encode(value.trim(), UTF8)

    private fun unescape(value: String): String {
        return value
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
    }
}
