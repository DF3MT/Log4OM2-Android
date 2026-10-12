package com.log4om.api.sync

import com.log4om.api.adif.AdifWriter
import com.log4om.api.logbook.LogbookService
import com.log4om.api.qso.QsoDto
import com.log4om.api.qso.QsoService
import com.log4om.api.security.UserPrincipal
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

data class SyncResultDto(
    val ok: Boolean,
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val skipped: Int = 0,
    val code: String,
    val message: String
)

@Service
class LogbookSyncService(
    private val logbooks: LogbookService,
    private val qsos: QsoService,
    private val qrz: QrzLogbookClient,
    private val eqsl: EqslClient
) {
    private val log = LoggerFactory.getLogger(LogbookSyncService::class.java)

    fun syncQrz(principal: UserPrincipal, id: UUID): SyncResultDto {
        val book = logbooks.requireOwned(principal, id)
        val key = logbooks.decryptQrzKey(book)
        if (key.isBlank()) {
            return missing("QRZ logbook API key is not set")
        }
        return try {
            val status = qrz.status(key)
            if (status.result != "OK") {
                return remote(RemotePayload.redact(status.reason.ifBlank { status.result }, listOf(key)))
            }
            val remoteQsos = qrz.fetchAll(key)
            val local = qsos.listAll(book.id)
            val plan = SyncPlanner.plan(local, remoteQsos)
            var uploaded = 0
            var skipped = 0
            for (qso in plan.toUpload) {
                val inserted = qrz.insert(key, qso)
                when (inserted.result) {
                    "OK" -> uploaded++
                    "REPLACE" -> skipped++
                    else -> {
                        val reason = inserted.reason
                        if (reason.contains("duplicate", ignoreCase = true)) {
                            skipped++
                        } else {
                            return SyncResultDto(
                                ok = false,
                                uploaded = uploaded,
                                skipped = skipped,
                                code = "REMOTE_ERROR",
                                message = RemotePayload.redact(reason.ifBlank { inserted.result }, listOf(key))
                            )
                        }
                    }
                }
            }
            val taken = local.mapNotNull { it.qsoid }.toMutableSet()
            val fresh = assignIds(plan.toDownload, taken, book.stationCallsign)
            val saved = if (fresh.isEmpty()) 0 else qsos.bulkInsertInto(book.id, fresh).inserted
            log.info("QRZ sync logbook={} uploaded={} downloaded={} skipped={}", book.id, uploaded, saved, skipped)
            SyncResultDto(
                ok = true,
                uploaded = uploaded,
                downloaded = saved,
                skipped = skipped,
                code = "OK",
                message = "QRZ sync finished"
            )
        } catch (e: RemoteLogException) {
            remote(RemotePayload.redact(e.message ?: "QRZ sync failed", listOf(key)))
        }
    }

    fun syncEqsl(principal: UserPrincipal, id: UUID): SyncResultDto {
        val book = logbooks.requireOwned(principal, id)
        val user = book.eqslUser.trim()
        val password = logbooks.decryptEqslPassword(book)
        if (user.isBlank() || password.isBlank()) {
            return missing("eQSL nickname and password are not set")
        }
        return try {
            val inbox = eqsl.downloadInbox(user, password)
            val local = qsos.listAll(book.id)
            val plan = SyncPlanner.plan(local, inbox)
            val upload = eqsl.upload(user, password, plan.toUpload)
            val taken = local.mapNotNull { it.qsoid }.toMutableSet()
            val fresh = assignIds(plan.toDownload, taken, book.stationCallsign)
            val saved = if (fresh.isEmpty()) 0 else qsos.bulkInsertInto(book.id, fresh).inserted
            log.info(
                "eQSL sync logbook={} uploaded={} downloaded={} skipped={}",
                book.id,
                upload.added,
                saved,
                upload.skipped
            )
            SyncResultDto(
                ok = true,
                uploaded = upload.added,
                downloaded = saved,
                skipped = upload.skipped,
                code = "OK",
                message = "eQSL sync finished"
            )
        } catch (e: RemoteLogException) {
            remote(RemotePayload.redact(e.message ?: "eQSL sync failed", listOf(user, password)))
        }
    }

    fun exportAdif(principal: UserPrincipal, id: UUID): String {
        val book = logbooks.requireOwned(principal, id)
        return AdifWriter.toAdif(qsos.listAll(book.id))
    }

    private fun missing(message: String) = SyncResultDto(
        ok = false,
        code = "MISSING_CREDENTIALS",
        message = message
    )

    private fun remote(message: String) = SyncResultDto(
        ok = false,
        code = "REMOTE_ERROR",
        message = message.ifBlank { "Remote sync failed" }
    )

    private fun assignIds(qsos: List<QsoDto>, taken: MutableSet<Long>, station: String): List<QsoDto> {
        var n = System.currentTimeMillis() * 1000
        return qsos.map { q ->
            do {
                n++
            } while (n in taken)
            taken += n
            val call = q.stationcallsign.ifBlank { station }
            q.copy(qsoid = n, stationcallsign = call)
        }
    }
}
