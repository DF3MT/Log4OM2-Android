package com.log4om.api.sync

import com.log4om.api.qso.QsoDto
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncPlannerTest {
    private val whenUtc = LocalDateTime.of(2024, 6, 1, 12, 30, 15)

    @Test
    fun identicalContactIsNeitherUploadedNorDownloaded() {
        val qso = sample("DL1ABC", whenUtc, "20m", "SSB")
        val plan = SyncPlanner.plan(listOf(qso), listOf(qso.copy(qsoid = 2)))
        assertTrue(plan.toUpload.isEmpty())
        assertTrue(plan.toDownload.isEmpty())
    }

    @Test
    fun localOnlyIsUploadedAndRemoteOnlyIsDownloaded() {
        val local = sample("DL1AAA", whenUtc, "40m", "CW")
        val remote = sample("G4BBB", whenUtc, "20m", "FT8")
        val plan = SyncPlanner.plan(listOf(local), listOf(remote))
        assertEquals(listOf("DL1AAA"), plan.toUpload.map { it.callsign })
        assertEquals(listOf("G4BBB"), plan.toDownload.map { it.callsign })
    }

    @Test
    fun callBandAndModeAreNormalized() {
        val local = sample("dl1abc", whenUtc, "20M", "ssb")
        val remote = sample("DL1ABC", whenUtc, "20m", "SSB")
        val plan = SyncPlanner.plan(listOf(local), listOf(remote))
        assertTrue(plan.toUpload.isEmpty())
        assertTrue(plan.toDownload.isEmpty())
    }

    @Test
    fun differentSecondIsADifferentContact() {
        val local = sample("DL1ABC", whenUtc, "20m", "FT8")
        val remote = sample("DL1ABC", whenUtc.withSecond(45), "20m", "FT8")
        val plan = SyncPlanner.plan(listOf(local), listOf(remote))
        assertEquals(1, plan.toUpload.size)
        assertEquals(1, plan.toDownload.size)
    }

    @Test
    fun minuteOnlyRemoteTimeMatchesAnySecondInThatMinute() {
        val local = sample("DL1ABC", whenUtc, "20m", "SSB", secondsExact = true)
        val remote = sample("DL1ABC", whenUtc.withSecond(0), "20m", "SSB", secondsExact = false)
        val plan = SyncPlanner.plan(listOf(local), listOf(remote))
        assertTrue(plan.toUpload.isEmpty())
        assertTrue(plan.toDownload.isEmpty())
    }

    @Test
    fun differentBandDoesNotMatch() {
        val local = sample("DL1ABC", whenUtc, "20m", "SSB")
        val remote = sample("DL1ABC", whenUtc, "40m", "SSB")
        assertFalse(SyncPlanner.plan(listOf(local), listOf(remote)).toUpload.isEmpty())
    }

    @Test
    fun qrzResponseDecodesAdifAndLogId() {
        val adif = "<CALL:4>W1AW<APP_QRZLOG_LOGID:9>130877825<EOR>"
        val encoded = java.net.URLEncoder.encode(adif, Charsets.UTF_8)
        val parsed = RemotePayload.qrzResult("RESULT=OK&COUNT=1&ADIF=$encoded")
        assertEquals("OK", parsed.result)
        assertEquals(listOf(130877825L), parsed.logIds)
        assertTrue(parsed.adif.contains("W1AW"))
    }

    @Test
    fun eqslDuplicateResultCountsAsSkipped() {
        val parsed = RemotePayload.parseEqslImport(
            "<html>Result: 0 out of 1 records added, 1 duplicate</html>"
        )
        assertTrue(parsed.ok)
        assertEquals(0, parsed.added)
        assertEquals(1, parsed.skipped)
    }

    @Test
    fun eqslAuthErrorIsNotOk() {
        val parsed = RemotePayload.parseEqslImport("Error: No match on eQSL_User/eQSL_Pswd")
        assertFalse(parsed.ok)
    }

    @Test
    fun redactStripsSecrets() {
        val clean = RemotePayload.redact("bad key ABC-SECRET-KEY", listOf("ABC-SECRET-KEY"))
        assertFalse(clean.contains("SECRET"))
    }

    private fun sample(
        call: String,
        whenUtc: LocalDateTime,
        band: String,
        mode: String,
        secondsExact: Boolean = true
    ) = QsoDto(
        qsoid = 1,
        callsign = call,
        band = band,
        mode = mode,
        qsodate = whenUtc,
        matchSecondsExact = secondsExact
    )
}
