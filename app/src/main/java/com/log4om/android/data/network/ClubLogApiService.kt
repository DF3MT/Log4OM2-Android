package com.log4om.android.data.network

import com.log4om.android.data.model.QrzCallsignData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Club Log DXCC entity lookup (not a full callbook).
 * Requires a Club Log API key: https://clublog.org/
 */
class ClubLogApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var apiKey: String = ""

    fun configure(apiKey: String) {
        this.apiKey = apiKey.trim()
    }

    val isConfigured: Boolean get() = apiKey.isNotBlank()

    suspend fun lookupDxcc(callsign: String, date: LocalDate = LocalDate.now()): Result<QrzCallsignData> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!isConfigured) throw Exception("Club Log API key not configured")
                val call = URLEncoder.encode(callsign.uppercase().trim(), StandardCharsets.UTF_8.name())
                val key = URLEncoder.encode(apiKey, StandardCharsets.UTF_8.name())
                val url = "https://clublog.org/dxcc?" +
                    "call=$call&api=$key&year=${date.year}&month=${date.monthValue}&day=${date.dayOfMonth}&full=1"
                val body = client.newCall(Request.Builder().url(url).build()).execute()
                    .body?.string() ?: throw Exception("Empty Club Log response")
                if (body.startsWith("Invalid") || body.startsWith("Error") || body.contains("API")) {
                    // Club Log returns plain-text errors for bad keys
                    if (!body.trimStart().startsWith("{") && !body.trimStart().startsWith("[")) {
                        throw Exception(body.trim().take(120))
                    }
                }
                val json = JSONObject(body)
                val dxcc = json.optInt("DXCC", json.optInt("dxcc", 0))
                val entity = json.optString("Name", json.optString("entity", ""))
                val cont = json.optString("Continent", json.optString("continent", ""))
                val cq = json.opt("CQZ")?.toString()?.takeIf { it != "null" }.orEmpty()
                val itu = json.opt("ITUZ")?.toString()?.takeIf { it != "null" }.orEmpty()
                if (dxcc <= 0 && entity.isBlank()) {
                    throw Exception("Club Log: no DXCC for $callsign")
                }
                QrzCallsignData(
                    call = callsign.uppercase(),
                    country = entity,
                    dxcc = if (dxcc > 0) dxcc.toString() else "",
                    continent = cont,
                    cqzone = cq,
                    ituzone = itu,
                    source = "Club Log"
                )
            }
        }
}
