package com.log4om.android.data.network

import com.log4om.android.data.auth.AuthTokenStore
import com.log4om.android.data.model.AuthTokens
import com.log4om.android.data.model.BulkInsertResult
import com.log4om.android.data.model.LogFilter
import com.log4om.android.data.model.LogbookConfig
import com.log4om.android.data.model.LogbookDraft
import com.log4om.android.data.model.Qso
import com.log4om.android.data.model.RemoteProbe
import com.log4om.android.data.model.RemoteSyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class Log4omApiService(
    private val tokens: AuthTokenStore
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val refreshMutex = Mutex()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "Log4OM-Android/1.0")
                    .header("Cache-Control", "no-store")
                    .build()
            )
        }
        .build()

    private fun base(): String = tokens.apiBaseUrl.trimEnd('/')

    suspend fun login(email: String, password: String, apiUrl: String? = null): Result<AuthTokens> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!apiUrl.isNullOrBlank()) tokens.apiBaseUrl = apiUrl
                val body = JSONObject()
                    .put("email", email.trim())
                    .put("password", password)
                val tokensResp = postAuth("/auth/login", body)
                tokens.saveSession(
                    accessToken = tokensResp.accessToken,
                    refreshToken = tokensResp.refreshToken,
                    email = tokensResp.email.ifBlank { email.trim() },
                    apiBaseUrl = apiUrl
                )
                tokensResp
            }
        }

    suspend fun register(
        email: String,
        password: String,
        displayName: String? = null,
        apiUrl: String? = null
    ): Result<AuthTokens> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!apiUrl.isNullOrBlank()) tokens.apiBaseUrl = apiUrl
                val body = JSONObject()
                    .put("email", email.trim())
                    .put("password", password)
                if (!displayName.isNullOrBlank()) body.put("displayName", displayName)
                val tokensResp = postAuth("/auth/register", body)
                tokens.saveSession(
                    accessToken = tokensResp.accessToken,
                    refreshToken = tokensResp.refreshToken,
                    email = tokensResp.email.ifBlank { email.trim() },
                    apiBaseUrl = apiUrl
                )
                tokensResp
            }
        }

    suspend fun logout(): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                runCatching { request("POST", "/auth/logout", auth = true) }
                tokens.clearSession()
            }
        }

    suspend fun listQsos(filter: LogFilter, limit: Int, offset: Int): Result<List<Qso>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = request("GET", "/qsos${QsoJson.filterQuery(filter, limit, offset)}")
                QsoJson.listFromJson(raw)
            }
        }

    suspend fun countQsos(filter: LogFilter): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = request("GET", "/qsos/count${QsoJson.filterQuery(filter)}")
                JSONObject(raw).optInt("count", 0)
            }
        }

    suspend fun filteredIds(filter: LogFilter): Result<List<Long>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = request("GET", "/qsos/ids${QsoJson.filterQuery(filter)}")
                val arr = JSONArray(raw)
                (0 until arr.length()).map { arr.getLong(it) }
            }
        }

    suspend fun qsosByIds(ids: List<Long>): Result<List<Qso>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (ids.isEmpty()) return@runCatching emptyList()
                val body = JSONObject().put("ids", JSONArray(ids))
                val raw = request("POST", "/qsos/by-ids", body = body)
                QsoJson.listFromJson(raw)
            }
        }

    suspend fun getQso(id: Long): Result<Qso> =
        withContext(Dispatchers.IO) {
            runCatching {
                QsoJson.fromJson(JSONObject(request("GET", "/qsos/$id")))
            }
        }

    suspend fun createQso(qso: Qso): Result<Qso> =
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = request("POST", "/qsos", body = QsoJson.toJson(qso))
                QsoJson.fromJson(JSONObject(raw))
            }
        }

    suspend fun updateQso(id: Long, qso: Qso): Result<Qso> =
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = request("PUT", "/qsos/$id", body = QsoJson.toJson(qso))
                QsoJson.fromJson(JSONObject(raw))
            }
        }

    suspend fun deleteQso(id: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                request("DELETE", "/qsos/$id")
                Unit
            }
        }

    suspend fun byCallsign(call: String, limit: Int = 15): Result<List<Qso>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val q = java.net.URLEncoder.encode(call, "UTF-8")
                QsoJson.listFromJson(request("GET", "/qsos/by-callsign?call=$q&limit=$limit"))
            }
        }

    suspend fun workedDxccIds(): Result<Set<Int>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val arr = JSONArray(request("GET", "/stats/worked-dxcc"))
                (0 until arr.length()).map { arr.getInt(it) }.toSet()
            }
        }

    suspend fun workedDxccBands(): Result<Set<Pair<Int, String>>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val arr = JSONArray(request("GET", "/stats/worked-dxcc-bands"))
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    o.getInt("dxcc") to o.getString("band")
                }.toSet()
            }
        }

    suspend fun exportAdif(ids: List<Long>): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().put("ids", JSONArray(ids))
                request("POST", "/adif/export", body = body)
            }
        }

    suspend fun importAdif(bytes: ByteArray, filename: String = "import.adi"): Result<BulkInsertResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val multipart = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        filename,
                        bytes.toRequestBody("application/octet-stream".toMediaType())
                    )
                    .build()
                val raw = request("POST", "/adif/import", multipart = multipart)
                val o = JSONObject(raw)
                BulkInsertResult(
                    inserted = o.optInt("inserted", 0),
                    skipped = o.optInt("skipped", 0)
                )
            }
        }

    suspend fun listLogbooks(): Result<List<LogbookConfig>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val arr = JSONArray(request("GET", "/me/logbooks"))
                (0 until arr.length()).map { parseLogbook(arr.getJSONObject(it)) }
            }
        }

    suspend fun saveLogbook(draft: LogbookDraft): Result<LogbookConfig> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = logbookBody(draft)
                val raw = if (draft.id.isBlank()) {
                    request("POST", "/me/logbooks", body = body)
                } else {
                    request("PUT", "/me/logbooks/${draft.id}", body = body)
                }
                parseLogbook(JSONObject(raw))
            }
        }

    suspend fun deleteLogbook(id: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                request("DELETE", "/me/logbooks/$id")
                Unit
            }
        }

    suspend fun activateLogbook(id: String): Result<LogbookConfig> =
        withContext(Dispatchers.IO) {
            runCatching { parseLogbook(JSONObject(request("POST", "/me/logbooks/$id/activate"))) }
        }

    suspend fun testLogbook(draft: LogbookDraft): Result<RemoteProbe> =
        withContext(Dispatchers.IO) {
            runCatching {
                val path = if (draft.id.isBlank()) {
                    "/me/logbooks/connection-test"
                } else {
                    "/me/logbooks/${draft.id}/test"
                }
                val o = JSONObject(request("POST", path, body = logbookBody(draft)))
                RemoteProbe(ok = o.optBoolean("ok"), message = o.optString("message"))
            }
        }

    suspend fun exportLogbook(id: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching { request("GET", "/me/logbooks/$id/adif") }
        }

    suspend fun syncLogbook(id: String, service: String): Result<RemoteSyncResult> =
        withContext(Dispatchers.IO) {
            runCatching { parseSync(JSONObject(request("POST", "/me/logbooks/$id/sync/$service"))) }
        }

    suspend fun getStation(): Result<JSONObject> =
        withContext(Dispatchers.IO) { runCatching { JSONObject(request("GET", "/me/station")) } }

    suspend fun putStation(body: JSONObject): Result<JSONObject> =
        withContext(Dispatchers.IO) { runCatching { JSONObject(request("PUT", "/me/station", body = body)) } }

    suspend fun getLookup(): Result<JSONObject> =
        withContext(Dispatchers.IO) { runCatching { JSONObject(request("GET", "/me/lookup-credentials")) } }

    suspend fun putLookup(body: JSONObject): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            runCatching { JSONObject(request("PUT", "/me/lookup-credentials", body = body)) }
        }

    suspend fun testLookup(body: JSONObject): Result<RemoteProbe> =
        withContext(Dispatchers.IO) {
            runCatching {
                val o = JSONObject(request("POST", "/me/lookup-credentials/test", body = body))
                val detail = listOf(o.optString("message"), o.optString("callsign"), o.optString("name"))
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                RemoteProbe(ok = o.optBoolean("ok"), message = detail)
            }
        }

    private fun logbookBody(draft: LogbookDraft): JSONObject {
        val o = JSONObject()
            .put("displayName", draft.displayName)
            .put("stationCallsign", draft.stationCallsign)
            .put("host", draft.host)
            .put("port", draft.port)
            .put("database", draft.database)
            .put("username", draft.username)
            .put("sslEnabled", draft.sslEnabled)
            .put("eqslUser", draft.eqslUser)
        if (draft.password.isNotBlank()) o.put("password", draft.password)
        if (draft.qrzLogbookKey.isNotBlank()) o.put("qrzLogbookKey", draft.qrzLogbookKey)
        if (draft.eqslPassword.isNotBlank()) o.put("eqslPassword", draft.eqslPassword)
        return o
    }

    private fun parseLogbook(o: JSONObject) = LogbookConfig(
        id = o.optString("id"),
        displayName = o.optString("displayName"),
        stationCallsign = o.optString("stationCallsign"),
        host = o.optString("host"),
        port = o.optInt("port", 3306),
        database = o.optString("database"),
        username = o.optString("username"),
        sslEnabled = o.optBoolean("sslEnabled"),
        passwordSet = o.optBoolean("passwordSet"),
        qrzLogbookKeySet = o.optBoolean("qrzLogbookKeySet"),
        eqslUser = o.optString("eqslUser"),
        eqslPasswordSet = o.optBoolean("eqslPasswordSet"),
        active = o.optBoolean("active")
    )

    private fun parseSync(o: JSONObject) = RemoteSyncResult(
        ok = o.optBoolean("ok"),
        uploaded = o.optInt("uploaded"),
        downloaded = o.optInt("downloaded"),
        skipped = o.optInt("skipped"),
        code = o.optString("code"),
        message = o.optString("message")
    )

    suspend fun testTenantDb(): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = request("POST", "/me/db-config/test")
                val o = JSONObject(raw)
                if (!o.optBoolean("ok", false)) {
                    throw Exception(o.optString("message", "DB test failed"))
                }
            }
        }

    private fun postAuth(path: String, body: JSONObject): AuthTokens {
        val req = Request.Builder()
            .url(base() + path)
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        client.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw Exception(parseError(text, res.code))
            return parseTokens(text)
        }
    }

    private fun parseTokens(text: String): AuthTokens {
        val o = JSONObject(text)
        return AuthTokens(
            accessToken = o.getString("accessToken"),
            refreshToken = o.getString("refreshToken"),
            email = o.optString("email"),
            userId = o.optString("userId"),
            tenantId = o.optString("tenantId"),
            expiresInSeconds = o.optLong("expiresInSeconds")
        )
    }

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        multipart: MultipartBody? = null,
        auth: Boolean = true,
        retried: Boolean = false
    ): String {
        val builder = Request.Builder().url(base() + path)
        if (auth) {
            val access = tokens.accessToken
                ?: throw Exception("Not logged in")
            builder.header("Authorization", "Bearer $access")
        }
        when {
            multipart != null -> builder.method(method, multipart)
            body != null -> builder.method(method, body.toString().toRequestBody(jsonMedia))
            method == "POST" || method == "PUT" || method == "DELETE" ->
                builder.method(method, ByteArray(0).toRequestBody(null))
            else -> builder.method(method, null)
        }
        client.newCall(builder.build()).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (res.code == 401 && auth && !retried) {
                val refreshed = refreshMutex.withLock { tryRefresh() }
                if (refreshed) {
                    return request(method, path, body, multipart, auth = true, retried = true)
                }
                tokens.clearSession()
                throw Exception("Session expired")
            }
            if (!res.isSuccessful) throw Exception(parseError(text, res.code))
            return text
        }
    }

    private fun tryRefresh(): Boolean {
        val refresh = tokens.refreshToken ?: return false
        return try {
            val body = JSONObject().put("refreshToken", refresh)
            val req = Request.Builder()
                .url(base() + "/auth/refresh")
                .post(body.toString().toRequestBody(jsonMedia))
                .build()
            client.newCall(req).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) return false
                val t = parseTokens(text)
                tokens.saveSession(t.accessToken, t.refreshToken, t.email.ifBlank { tokens.email.orEmpty() })
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun parseError(text: String, code: Int): String {
        if (text.isBlank()) return "HTTP $code"
        return runCatching {
            val o = JSONObject(text)
            o.optString("message").ifBlank {
                o.optString("error").ifBlank { "HTTP $code" }
            }
        }.getOrElse { text.take(200) }
    }
}
