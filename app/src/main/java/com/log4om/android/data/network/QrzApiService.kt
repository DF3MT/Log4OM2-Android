package com.log4om.android.data.network

import com.log4om.android.data.model.QrzCallsignData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.concurrent.TimeUnit

class QrzApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var sessionKey: String? = null
    private var qrzUser: String = ""
    private var qrzPassword: String = ""

    fun configure(user: String, password: String) {
        if (user != qrzUser || password != qrzPassword) {
            sessionKey = null
        }
        qrzUser = user
        qrzPassword = password
    }

    suspend fun lookupCallsign(callsign: String): Result<QrzCallsignData> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (qrzUser.isBlank()) throw Exception("QRZ Benutzername nicht konfiguriert")
                if (sessionKey == null) login()
                val result = fetchCallsign(callsign)
                if (result.error != null && result.error.contains("Session Timeout", ignoreCase = true)) {
                    sessionKey = null
                    login()
                    fetchCallsign(callsign)
                } else {
                    result
                }
            }
        }

    private fun login() {
        val url = "https://xmldata.qrz.com/xml/current/" +
                "?username=${qrzUser.trim()}" +
                "&password=${qrzPassword.trim()}" +
                "&agent=Log4OM-Android-1.0"
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        val body = response.body?.string() ?: throw Exception("Leere Antwort von QRZ")
        sessionKey = parseTag(body, "Key")
            ?: throw Exception("QRZ Login fehlgeschlagen: ${parseTag(body, "Error") ?: "Unbekannt"}")
    }

    private fun fetchCallsign(callsign: String): QrzCallsignData {
        val key = sessionKey ?: throw Exception("Keine QRZ Session")
        val url = "https://xmldata.qrz.com/xml/current/?s=$key&callsign=${callsign.uppercase()}"
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        val body = response.body?.string() ?: throw Exception("Leere Antwort")
        return parseCallsignResponse(body)
    }

    private fun parseTag(xml: String, tag: String): String? {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))
        var event = parser.eventType
        var lastTag = ""
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> lastTag = parser.name
                XmlPullParser.TEXT -> if (lastTag == tag) return parser.text.trim()
            }
            event = parser.next()
        }
        return null
    }

    private fun parseCallsignResponse(xml: String): QrzCallsignData {
        val fields = mutableMapOf<String, String>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))
        var inCallsign = false
        var currentTag = ""
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag == "Callsign") inCallsign = true
                }
                XmlPullParser.TEXT -> {
                    if (inCallsign && currentTag.isNotEmpty()) {
                        fields[currentTag] = parser.text.trim()
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "Callsign") inCallsign = false
                    currentTag = ""
                }
            }
            event = parser.next()
        }

        val errorMsg = if (fields.isEmpty()) {
            parseTag(xml, "Error") ?: parseTag(xml, "Remark")
        } else null

        val fname = fields["fname"] ?: ""
        val lname = fields["name"] ?: ""
        val fullName = listOf(fname, lname).filter { it.isNotBlank() }.joinToString(" ")

        return QrzCallsignData(
            call      = fields["call"] ?: "",
            fname     = fname,
            name      = fullName,
            addr1     = fields["addr1"] ?: "",
            addr2     = fields["addr2"] ?: "",
            state     = fields["state"] ?: "",
            country   = fields["country"] ?: "",
            cqzone    = fields["cqzone"] ?: "",
            ituzone   = fields["ituzone"] ?: "",
            lat       = fields["lat"] ?: "",
            lon       = fields["lon"] ?: "",
            grid      = fields["grid"] ?: "",
            email     = fields["email"] ?: "",
            dxcc      = fields["dxcc"] ?: "",
            continent = fields["continent"] ?: "",
            pfx       = fields["pfx"] ?: "",
            bio       = fields["bio"] ?: "",
            image     = fields["image"] ?: "",
            error     = errorMsg
        )
    }
}
