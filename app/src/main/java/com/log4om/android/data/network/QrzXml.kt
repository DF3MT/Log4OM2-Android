package com.log4om.android.data.network

import com.log4om.android.data.model.QrzCallsignData
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal object QrzXml {

    private val UTF8 = StandardCharsets.UTF_8.name()

    fun loginUrl(user: String, password: String): String {
        val u = enc(user.trim())
        val p = enc(password.trim())
        return "https://xmldata.qrz.com/xml/current/?username=$u&password=$p&agent=Log4OM-Android-1.0"
    }

    fun lookupUrl(sessionKey: String, callsign: String): String {
        val s = enc(sessionKey)
        val c = enc(callsign.uppercase().trim())
        return "https://xmldata.qrz.com/xml/current/?s=$s&callsign=$c"
    }

    fun tag(xml: String, name: String): String? {
        val re = Regex(
            """<(?:[\w.-]+:)?${Regex.escape(name)}(?:\s[^>]*)?>([\s\S]*?)</(?:[\w.-]+:)?${Regex.escape(name)}>""",
            RegexOption.IGNORE_CASE
        )
        return re.find(xml)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun parseCallsign(xml: String): QrzCallsignData {
        val block = tag(xml, "Callsign")
        if (block == null) {
            return QrzCallsignData(
                error = tag(xml, "Error") ?: tag(xml, "Remark"),
                source = "QRZ"
            )
        }
        fun f(name: String) = tag(block, name).orEmpty()
        val fname = f("fname")
        val lname = f("name")
        val fullName = listOf(fname, lname).filter { it.isNotBlank() }.joinToString(" ")
        return QrzCallsignData(
            call = f("call"),
            fname = fname,
            name = fullName,
            addr1 = f("addr1"),
            addr2 = f("addr2"),
            state = f("state"),
            country = f("country"),
            cqzone = f("cqzone"),
            ituzone = f("ituzone"),
            lat = f("lat"),
            lon = f("lon"),
            grid = f("grid"),
            email = f("email"),
            dxcc = f("dxcc"),
            continent = f("continent"),
            pfx = f("pfx"),
            bio = f("bio"),
            image = f("image"),
            error = null,
            source = "QRZ"
        )
    }

    private fun enc(value: String): String = URLEncoder.encode(value, UTF8)
}
