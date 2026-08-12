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

class HamQthApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var sessionId: String? = null
    private var user: String = ""
    private var password: String = ""

    fun configure(user: String, password: String) {
        if (user != this.user || password != this.password) sessionId = null
        this.user = user
        this.password = password
    }

    val isConfigured: Boolean get() = user.isNotBlank() && password.isNotBlank()

    suspend fun lookupCallsign(callsign: String): Result<QrzCallsignData> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!isConfigured) throw Exception("HamQTH not configured")
                if (sessionId == null) login()
                val data = fetch(callsign)
                if (data.error?.contains("Session", ignoreCase = true) == true) {
                    sessionId = null
                    login()
                    fetch(callsign)
                } else data
            }
        }

    private fun login() {
        val url = "https://www.hamqth.com/xml.php?u=${user.trim()}&p=${password.trim()}"
        val body = client.newCall(Request.Builder().url(url).build()).execute()
            .body?.string() ?: throw Exception("Empty HamQTH login response")
        sessionId = parseFirst(body, "session_id")
            ?: throw Exception(parseFirst(body, "error") ?: "HamQTH login failed")
    }

    private fun fetch(callsign: String): QrzCallsignData {
        val id = sessionId ?: throw Exception("No HamQTH session")
        val url = "https://www.hamqth.com/xml.php?id=$id&callsign=${callsign.uppercase()}&prg=Log4OMAndroid"
        val body = client.newCall(Request.Builder().url(url).build()).execute()
            .body?.string() ?: throw Exception("Empty HamQTH response")
        return parseSearch(body)
    }

    private fun parseFirst(xml: String, tag: String): String? {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))
        var event = parser.eventType
        var last = ""
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> last = parser.name.substringAfterLast(':')
                XmlPullParser.TEXT -> if (last.equals(tag, ignoreCase = true)) return parser.text.trim()
            }
            event = parser.next()
        }
        return null
    }

    private fun parseSearch(xml: String): QrzCallsignData {
        val fields = mutableMapOf<String, String>()
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))
        var inSearch = false
        var tag = ""
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name.substringAfterLast(':')
                    if (tag.equals("search", ignoreCase = true)) inSearch = true
                }
                XmlPullParser.TEXT -> if (inSearch && tag.isNotEmpty()) fields[tag.lowercase()] = parser.text.trim()
                XmlPullParser.END_TAG -> {
                    if (parser.name.substringAfterLast(':').equals("search", ignoreCase = true)) inSearch = false
                    tag = ""
                }
            }
            event = parser.next()
        }

        val err = if (fields.isEmpty()) parseFirst(xml, "error") else null
        if (err != null) {
            return QrzCallsignData(error = err, source = "HamQTH")
        }

        val nick = fields["nick"].orEmpty()
        val adrName = fields["adr_name"].orEmpty()
        val name = adrName.ifBlank { nick }
        return QrzCallsignData(
            call = fields["callsign"].orEmpty(),
            fname = "",
            name = name,
            addr1 = fields["adr_street1"].orEmpty(),
            addr2 = fields["qth"] ?: fields["adr_city"].orEmpty(),
            state = fields["district"].orEmpty(),
            country = fields["country"] ?: fields["adr_country"].orEmpty(),
            cqzone = fields["cq"].orEmpty(),
            ituzone = fields["itu"].orEmpty(),
            lat = fields["latitude"].orEmpty(),
            lon = fields["longitude"].orEmpty(),
            grid = fields["grid"].orEmpty().uppercase(),
            email = fields["email"].orEmpty(),
            dxcc = fields["adif"].orEmpty(),
            continent = fields["continent"].orEmpty(),
            pfx = "",
            bio = "",
            image = fields["picture"].orEmpty(),
            error = null,
            source = "HamQTH"
        )
    }
}
