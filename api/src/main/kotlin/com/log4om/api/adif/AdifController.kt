package com.log4om.api.adif

import com.log4om.api.qso.QsoDto
import com.log4om.api.qso.QsoService
import com.log4om.api.security.UserPrincipal
import java.time.format.DateTimeFormatter
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

data class ExportRequest(val ids: List<Long>)

@RestController
@RequestMapping("/adif")
class AdifController(
    private val qsoService: QsoService
) {
    @PostMapping("/export-full")
    fun exportFull(
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<ByteArray> = adifResponse(qsoService.listAll(principal), "logbook.adi")

    @PostMapping("/export")
    fun export(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody body: ExportRequest
    ): ResponseEntity<ByteArray> {
        return adifResponse(qsoService.byIds(principal, body.ids), "export.adi")
    }

    private fun adifResponse(qsos: List<QsoDto>, filename: String): ResponseEntity<ByteArray> {
        val adif = AdifWriter.toAdif(qsos)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .contentType(MediaType.parseMediaType("text/plain; charset=UTF-8"))
            .body(adif.toByteArray(Charsets.UTF_8))
    }

    @PostMapping("/import", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun importAdif(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam("file") file: MultipartFile
    ): Map<String, Any> {
        val text = file.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val qsos = AdifParser.parse(text)
        val result = qsoService.bulkInsert(principal, qsos)
        return mapOf(
            "inserted" to result.inserted,
            "skipped" to result.skipped,
            "parsed" to qsos.size
        )
    }
}

object AdifWriter {
    private val dateFmt = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val timeFmt = DateTimeFormatter.ofPattern("HHmmss")

    fun toAdif(qsos: List<QsoDto>): String = buildString {
        appendLine("ADIF Export from Log4OM API")
        appendLine("<ADIF_VER:5>3.1.4")
        appendLine("<PROGRAMID:10>Log4OM API")
        appendLine("<EOH>")
        qsos.forEach { appendRecord(this, it) }
    }

    /** One ADIF record, no header. QRZ INSERT accepts a single record. */
    fun record(qso: QsoDto): String = buildString { appendRecord(this, qso) }

    private fun appendRecord(out: StringBuilder, q: QsoDto) {
        fun field(name: String, value: String?) {
            if (value.isNullOrBlank()) return
            out.append("<$name:${value.length}>$value")
        }
        field("CALL", q.callsign)
        field("BAND", q.band)
        field("MODE", q.mode)
        field("QSO_DATE", q.qsodate.format(dateFmt))
        field("TIME_ON", q.qsodate.format(timeFmt))
        field("STATION_CALLSIGN", q.stationcallsign)
        if (q.freq > 0) field("FREQ", (q.freq / 1000.0).toString())
        field("RST_SENT", q.rstsent)
        field("RST_RCVD", q.rstrcvd)
        field("NAME", q.name)
        field("QTH", q.qth)
        field("COUNTRY", q.country)
        if (q.dxcc > 0) field("DXCC", q.dxcc.toString())
        field("GRIDSQUARE", q.gridsquare)
        field("COMMENT", q.comment)
        field("SOTA_REF", q.sotaRef)
        field("IOTA", q.iota)
        field("POTA_REF", q.potaRef)
        field("WWFF_REF", q.wwffRef)
        field("COTA_REF", q.cotaRef)
        out.appendLine("<EOR>")
    }
}

object AdifParser {
    private val fieldRe = Regex("""<([A-Za-z0-9_]+):(\d+)(?::[^>]*)?>([\s\S]*?)(?=<|$)""")

    fun parse(text: String): List<QsoDto> {
        val body = text.substringAfter("<EOH>", text).substringAfter("<eoh>", text)
        val records = body.split(Regex("(?i)<EOR>")).map { it.trim() }.filter { it.isNotEmpty() }
        return records.mapNotNull { rec ->
            val map = linkedMapOf<String, String>()
            fieldRe.findAll(rec).forEach { m ->
                val name = m.groupValues[1].uppercase()
                val len = m.groupValues[2].toIntOrNull() ?: return@forEach
                val raw = m.groupValues[3]
                map[name] = raw.take(len)
            }
            if (map["CALL"].isNullOrBlank()) return@mapNotNull null
            val date = map["QSO_DATE"].orEmpty()
            val timeRaw = map["TIME_ON"].orEmpty().trim()
            val timeDigits = timeRaw.filter { it.isDigit() }
            val time = timeDigits.padEnd(6, '0').take(6)
            val secondsExact = timeDigits.length >= 6
            val dt = runCatching {
                java.time.LocalDateTime.of(
                    date.substring(0, 4).toInt(),
                    date.substring(4, 6).toInt(),
                    date.substring(6, 8).toInt(),
                    time.substring(0, 2).toInt(),
                    time.substring(2, 4).toInt(),
                    time.substring(4, 6).toInt()
                )
            }.getOrElse { java.time.LocalDateTime.now() }
            val freqMhz = map["FREQ"]?.toDoubleOrNull() ?: 0.0
            QsoDto(
                qsoid = System.currentTimeMillis() + (map["CALL"].hashCode() and 0xffff),
                callsign = map["CALL"].orEmpty(),
                band = map["BAND"].orEmpty(),
                mode = map["MODE"].orEmpty(),
                qsodate = dt,
                freq = freqMhz * 1000.0,
                rstsent = map["RST_SENT"] ?: "59",
                rstrcvd = map["RST_RCVD"] ?: "59",
                name = map["NAME"].orEmpty(),
                qth = map["QTH"].orEmpty(),
                country = map["COUNTRY"].orEmpty(),
                dxcc = map["DXCC"]?.toIntOrNull() ?: 0,
                gridsquare = map["GRIDSQUARE"].orEmpty(),
                comment = map["COMMENT"].orEmpty(),
                sotaRef = map["SOTA_REF"].orEmpty(),
                iota = map["IOTA"].orEmpty(),
                potaRef = map["POTA_REF"].orEmpty(),
                wwffRef = map["WWFF_REF"].orEmpty(),
                cotaRef = map["COTA_REF"].orEmpty(),
                matchSecondsExact = secondsExact
            )
        }
    }
}
