package com.log4om.api.sync

import com.log4om.api.adif.AdifParser
import com.log4om.api.adif.AdifWriter
import com.log4om.api.qso.QsoDto
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import org.springframework.stereotype.Component

data class QrzCallResult(
    val result: String,
    val reason: String = "",
    val count: Int = 0,
    val adif: String = "",
    val logIds: List<Long> = emptyList()
)

data class EqslImportResult(
    val ok: Boolean,
    val added: Int = 0,
    val skipped: Int = 0,
    val message: String = ""
)

object RemotePayload {
    private val utf8 = StandardCharsets.UTF_8

    fun form(fields: Map<String, String>): String =
        fields.entries.joinToString("&") { (k, v) ->
            URLEncoder.encode(k, utf8) + "=" + URLEncoder.encode(v, utf8)
        }

    fun parseQrz(body: String): Map<String, String> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return emptyMap()
        val map = linkedMapOf<String, String>()
        trimmed.split("&").forEach { part ->
            if (part.isEmpty()) return@forEach
            val i = part.indexOf('=')
            if (i < 0) return@forEach
            val key = URLDecoder.decode(part.substring(0, i), utf8).trim().uppercase()
            val value = URLDecoder.decode(part.substring(i + 1), utf8)
            map[key] = value
        }
        return map
    }

    fun qrzResult(body: String): QrzCallResult {
        val trimmed = body.trim()
        if (trimmed.startsWith("<") || trimmed.startsWith("ADIF", ignoreCase = true)) {
            return QrzCallResult(result = "OK", adif = trimmed, logIds = qrzLogIds(trimmed))
        }
        val map = parseQrz(trimmed)
        val adif = map["ADIF"].orEmpty()
        val ids = qrzLogIds(adif).ifEmpty {
            map["LOGIDS"].orEmpty().split(",", "+", " ")
                .mapNotNull { it.trim().toLongOrNull() }
        }
        return QrzCallResult(
            result = map["RESULT"].orEmpty().uppercase(),
            reason = map["REASON"].orEmpty(),
            count = map["COUNT"]?.toIntOrNull() ?: 0,
            adif = adif,
            logIds = ids
        )
    }

    fun qrzLogIds(adif: String): List<Long> {
        val re = Regex("(?i)<APP_QRZLOG_LOGID:(\\d+)>(\\d+)")
        return re.findAll(adif).mapNotNull { m ->
            val len = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            m.groupValues[2].take(len).toLongOrNull()
        }.toList()
    }

    fun redact(text: String, secrets: List<String>): String {
        var out = text.replace(Regex("\\s+"), " ").trim()
        secrets.filter { it.length >= 3 }.forEach { secret ->
            out = out.replace(secret, "***")
        }
        return out.take(240)
    }

    fun parseEqslImport(html: String): EqslImportResult {
        val flat = html.replace(Regex("\\s+"), " ").trim()
        val lower = flat.lowercase()
        val added = Regex("(?i)result:\\s*(\\d+)\\s+out of\\s+(\\d+)")
            .find(flat)
        val dup = Regex("(?i)(\\d+)\\s+duplicates?").find(flat)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        if (added != null) {
            val n = added.groupValues[1].toInt()
            return EqslImportResult(ok = true, added = n, skipped = dup, message = flat.take(240))
        }
        if (lower.contains("error:")) {
            return EqslImportResult(ok = false, message = flat.take(240))
        }
        if (lower.contains("duplicate")) {
            return EqslImportResult(ok = true, added = 0, skipped = dup.coerceAtLeast(1), message = flat.take(240))
        }
        return EqslImportResult(ok = false, message = flat.take(240).ifBlank { "eQSL upload failed" })
    }

    fun inboxIsEmpty(body: String): Boolean {
        val lower = body.lowercase()
        if (lower.contains("<eoh>") || lower.contains("<call:") || lower.contains("<eor>")) return false
        return lower.contains("no qso") ||
            lower.contains("no record") ||
            lower.contains("no log") ||
            lower.contains("inbox is empty") ||
            lower.contains("you have no") ||
            body.isBlank()
    }
}

@Component
class QrzLogbookClient {
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun status(apiKey: String): QrzCallResult = call(apiKey, "STATUS")

    fun fetchAll(apiKey: String): List<QsoDto> {
        val all = mutableListOf<QsoDto>()
        var after = 0L
        var guard = 0
        while (guard++ < 500) {
            val option = "MAX:$PAGE,AFTERLOGID:$after,TYPE:ADIF"
            val page = call(apiKey, "FETCH", option = option)
            if (page.result == "FAIL" || page.result == "AUTH") {
                if (all.isEmpty() && page.reason.contains("no ", ignoreCase = true)) return emptyList()
                if (page.result != "OK" && all.isEmpty()) {
                    throw RemoteLogException(page.reason.ifBlank { "QRZ FETCH ${page.result}" })
                }
                break
            }
            if (page.result != "OK" && page.adif.isBlank()) break
            val parsed = if (page.adif.isBlank()) emptyList() else AdifParser.parse(page.adif)
            if (parsed.isEmpty()) break
            all += parsed
            if (parsed.size < PAGE) break
            val maxId = page.logIds.maxOrNull() ?: break
            val next = maxId + 1
            if (next <= after) break
            after = next
        }
        return all
    }

    fun insert(apiKey: String, qso: QsoDto): QrzCallResult =
        call(apiKey, "INSERT", adif = AdifWriter.record(qso))

    private fun call(apiKey: String, action: String, adif: String? = null, option: String? = null): QrzCallResult {
        val fields = linkedMapOf("KEY" to apiKey, "ACTION" to action)
        if (!adif.isNullOrBlank()) fields["ADIF"] = adif
        if (!option.isNullOrBlank()) fields["OPTION"] = option
        val body = post("https://logbook.qrz.com/api", RemotePayload.form(fields), 45)
        return RemotePayload.qrzResult(body)
    }

    private fun post(url: String, form: String, timeoutSeconds: Long): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/plain, */*")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (response.statusCode() !in 200..299) {
            throw RemoteLogException("QRZ HTTP ${response.statusCode()}")
        }
        return response.body()
    }

    companion object {
        const val PAGE = 250
        const val USER_AGENT = "Log4OM-API/1.0 (DF3MT)"
    }
}

@Component
class EqslClient {
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun downloadInbox(user: String, password: String): List<QsoDto> {
        val body = post(
            "https://www.eqsl.cc/qslcard/DownloadInBox.cfm",
            RemotePayload.form(mapOf("UserID" to user, "Password" to password)),
            60
        )
        if (RemotePayload.inboxIsEmpty(body)) return emptyList()
        val lower = body.lowercase()
        if (lower.contains("error:") && !lower.contains("<call:")) {
            throw RemoteLogException(RemotePayload.redact(body, listOf(user, password)))
        }
        return AdifParser.parse(body)
    }

    fun upload(user: String, password: String, qsos: List<QsoDto>): EqslImportResult {
        if (qsos.isEmpty()) return EqslImportResult(ok = true, message = "Nothing to upload")
        var added = 0
        var skipped = 0
        qsos.chunked(40).forEach { chunk ->
            val adif = AdifWriter.toAdif(chunk)
            val body = post(
                "https://www.eqsl.cc/qslcard/ImportADIF.cfm",
                RemotePayload.form(
                    mapOf(
                        "EQSL_USER" to user,
                        "EQSL_PSWD" to password,
                        "ADIFData" to adif
                    )
                ),
                60
            )
            val parsed = RemotePayload.parseEqslImport(body)
            if (!parsed.ok) {
                throw RemoteLogException(RemotePayload.redact(parsed.message, listOf(user, password)))
            }
            added += parsed.added
            skipped += parsed.skipped
        }
        return EqslImportResult(ok = true, added = added, skipped = skipped, message = "added $added")
    }

    private fun post(url: String, form: String, timeoutSeconds: Long): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("User-Agent", "Log4OM-API/1.0 (DF3MT)")
            .header("Accept", "text/plain, text/html, */*")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (response.statusCode() !in 200..299) {
            throw RemoteLogException("eQSL HTTP ${response.statusCode()}")
        }
        return response.body()
    }
}

class RemoteLogException(message: String) : RuntimeException(message)
