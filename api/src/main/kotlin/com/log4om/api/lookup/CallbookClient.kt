package com.log4om.api.lookup

import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset

internal const val LOOKUP_CONNECT_TIMEOUT_SEC = 10L
internal const val LOOKUP_READ_TIMEOUT_SEC = 15L

class LookupProviderException(message: String) : RuntimeException(message)

data class LookupHit(
    val callsign: String,
    val name: String,
    val detail: String
)

fun interface LookupHttp {
    fun get(url: String): String
}

/**
 * Session login + callsign lookup for QRZ and HamQTH, API-key DXCC lookup for Club Log.
 * Matches the Android OkHttp clients (10s connect, 15s read).
 */
class CallbookClient(
    private val http: LookupHttp,
    private val today: () -> LocalDate = { LocalDate.now(ZoneOffset.UTC) }
) : CallbookProbe {
    override fun testQrz(user: String, password: String, callsign: String): LookupHit =
        guarded(listOf(password)) {
            val key = qrzLogin(user, password)
            var parsed = qrzFetch(key, callsign)
            if (parsed.error?.contains("Session Timeout", ignoreCase = true) == true) {
                parsed = qrzFetch(qrzLogin(user, password), callsign)
            }
            val err = parsed.error
            if (!err.isNullOrBlank()) throw LookupProviderException(err)
            LookupHit(
                callsign = parsed.call.ifBlank { callsign.uppercase() },
                name = parsed.name,
                detail = listOf(parsed.country, parsed.grid).filter { it.isNotBlank() }.joinToString(" · ")
            )
        }

    override fun testHamqth(user: String, password: String, callsign: String): LookupHit =
        guarded(listOf(password)) {
            val id = hamqthLogin(user, password)
            var parsed = hamqthFetch(id, callsign)
            if (parsed.error?.contains("Session", ignoreCase = true) == true) {
                parsed = hamqthFetch(hamqthLogin(user, password), callsign)
            }
            val err = parsed.error
            if (!err.isNullOrBlank()) throw LookupProviderException(err)
            LookupHit(
                callsign = parsed.call.ifBlank { callsign.uppercase() },
                name = parsed.name,
                detail = listOf(parsed.country, parsed.grid).filter { it.isNotBlank() }.joinToString(" · ")
            )
        }

    override fun testClubLog(apiKey: String, callsign: String): LookupHit =
        guarded(listOf(apiKey)) {
            val body = http.get(LookupXml.clubLogUrl(apiKey, callsign, today()))
            parseClubLog(body, callsign)
        }

    private fun qrzLogin(user: String, password: String): String {
        val body = http.get(LookupXml.qrzLoginUrl(user, password))
        return LookupXml.tag(body, "Key")
            ?: throw LookupProviderException(LookupXml.tag(body, "Error") ?: "QRZ login failed")
    }

    private fun qrzFetch(sessionKey: String, callsign: String) =
        LookupXml.parseQrzCallsign(http.get(LookupXml.qrzLookupUrl(sessionKey, callsign)))

    private fun hamqthLogin(user: String, password: String): String {
        val body = http.get(LookupXml.hamqthLoginUrl(user, password))
        return LookupXml.tag(body, "session_id")
            ?: throw LookupProviderException(LookupXml.tag(body, "error") ?: "HamQTH login failed")
    }

    private fun hamqthFetch(sessionId: String, callsign: String) =
        LookupXml.parseHamQth(http.get(LookupXml.hamqthLookupUrl(sessionId, callsign)))

    private fun <T> guarded(secrets: List<String>, block: () -> T): T {
        try {
            return block()
        } catch (ex: LookupProviderException) {
            throw LookupProviderException(redactSecrets(ex.message ?: "Lookup failed", secrets))
        } catch (ex: Exception) {
            throw LookupProviderException(redactSecrets(ex.message ?: "Lookup failed", secrets))
        }
    }
}

internal fun parseClubLog(body: String, callsign: String): LookupHit {
    val trimmed = body.trim()
    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
        throw LookupProviderException(trimmed.lineSequence().first().take(160).ifBlank { "Club Log error" })
    }
    val json = try {
        CLUBLOG_JSON.readTree(trimmed)
    } catch (_: Exception) {
        throw LookupProviderException(trimmed.lineSequence().first().take(160).ifBlank { "Club Log error" })
    }
    val dxcc = when {
        json.hasNonNull("DXCC") -> json.path("DXCC").asInt(0)
        else -> json.path("dxcc").asInt(0)
    }
    val entity = json.path("Name").asText("").ifBlank { json.path("entity").asText("") }
    val cont = json.path("Continent").asText("").ifBlank { json.path("continent").asText("") }
    if (dxcc <= 0 && entity.isBlank()) {
        throw LookupProviderException("Club Log: no DXCC for ${callsign.uppercase()}")
    }
    val detail = listOf(
        if (dxcc > 0) "DXCC $dxcc" else "",
        cont
    ).filter { it.isNotBlank() }.joinToString(" · ")
    return LookupHit(
        callsign = callsign.uppercase(),
        name = entity,
        detail = detail
    )
}

internal fun redactSecrets(message: String, secrets: List<String>): String {
    var out = message
    for (secret in secrets) {
        val value = secret.trim()
        if (value.length >= 4) out = out.replace(value, "***")
    }
    return out.take(400)
}

private val CLUBLOG_JSON = ObjectMapper()

internal class JdkLookupHttp : LookupHttp {
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(LOOKUP_CONNECT_TIMEOUT_SEC))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    override fun get(url: String): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(LOOKUP_READ_TIMEOUT_SEC))
            .header("User-Agent", "Log4OM-Android/1.0")
            .GET()
            .build()
        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw LookupProviderException("Lookup request interrupted")
        } catch (ex: Exception) {
            throw LookupProviderException(publicTransportMessage(ex))
        }
        val body = response.body().orEmpty().let { raw ->
            if (raw.length > MAX_BODY) raw.substring(0, MAX_BODY) else raw
        }
        if (response.statusCode() !in 200..299) {
            val snippet = body.trim().lineSequence().firstOrNull().orEmpty().take(160)
            val msg = if (snippet.isBlank()) "HTTP ${response.statusCode()}" else "HTTP ${response.statusCode()}: $snippet"
            throw LookupProviderException(msg)
        }
        if (body.isBlank()) throw LookupProviderException("Empty response")
        return body
    }

    private fun publicTransportMessage(ex: Exception): String {
        val raw = ex.message?.trim().orEmpty()
        val noUrl = raw.replace(Regex("""https?://\S+"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
        return noUrl.ifBlank { "Lookup request failed" }
    }

    companion object {
        private const val MAX_BODY = 65_536
    }
}
