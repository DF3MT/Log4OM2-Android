package com.log4om.android.data.network

import com.log4om.android.data.model.QrzCallsignData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class QrzApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "Log4OM-Android/1.0")
                    .build()
            )
        }
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

    val isConfigured: Boolean get() = qrzUser.isNotBlank() && qrzPassword.isNotBlank()

    suspend fun lookupCallsign(callsign: String): Result<QrzCallsignData> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!isConfigured) throw Exception("QRZ Benutzername/Passwort nicht konfiguriert")
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
        val url = QrzXml.loginUrl(qrzUser, qrzPassword)
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        val body = response.body?.string() ?: throw Exception("Leere Antwort von QRZ")
        sessionKey = QrzXml.tag(body, "Key")
            ?: throw Exception("QRZ Login fehlgeschlagen: ${QrzXml.tag(body, "Error") ?: "Unbekannt"}")
    }

    private fun fetchCallsign(callsign: String): QrzCallsignData {
        val key = sessionKey ?: throw Exception("Keine QRZ Session")
        val url = QrzXml.lookupUrl(key, callsign)
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        val body = response.body?.string() ?: throw Exception("Leere Antwort")
        return QrzXml.parseCallsign(body)
    }
}
