package com.log4om.api.logbook

import com.log4om.api.security.UserPrincipal
import com.log4om.api.sync.LogbookSyncService
import com.log4om.api.tenant.DbConfigRequest
import java.util.UUID
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/me/logbooks")
class LogbookController(
    private val logbooks: LogbookService,
    private val sync: LogbookSyncService
) {
    @GetMapping
    fun list(@AuthenticationPrincipal principal: UserPrincipal) = logbooks.list(principal)

    @PostMapping
    fun create(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody body: LogbookWriteRequest
    ) = logbooks.create(principal, body)

    @PostMapping("/connection-test")
    fun testDraft(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody body: LogbookWriteRequest
    ) = logbooks.test(principal, null, body.toDbRequest())

    @GetMapping("/{id}")
    fun get(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: UUID
    ) = logbooks.get(principal, id)

    @PutMapping("/{id}")
    fun update(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: UUID,
        @RequestBody body: LogbookWriteRequest
    ) = logbooks.update(principal, id, body)

    @DeleteMapping("/{id}")
    fun delete(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: UUID
    ) {
        logbooks.delete(principal, id)
    }

    @PostMapping("/{id}/activate")
    fun activate(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: UUID
    ) = logbooks.activate(principal, id)

    @PostMapping("/{id}/test")
    fun test(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: UUID,
        @RequestBody(required = false) body: LogbookWriteRequest?
    ) = logbooks.test(principal, id, body?.toDbRequest())

    @GetMapping("/{id}/adif")
    fun export(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: UUID
    ): ResponseEntity<ByteArray> {
        val book = logbooks.get(principal, id)
        val adif = sync.exportAdif(principal, id)
        val safe = (book.stationCallsign.ifBlank { book.displayName })
            .replace(Regex("[^A-Za-z0-9_-]"), "")
            .ifBlank { "logbook" }
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$safe.adi\"")
            .contentType(MediaType.parseMediaType("text/plain; charset=UTF-8"))
            .body(adif.toByteArray(Charsets.UTF_8))
    }

    @PostMapping("/{id}/sync/qrz")
    fun syncQrz(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: UUID
    ) = sync.syncQrz(principal, id)

    @PostMapping("/{id}/sync/eqsl")
    fun syncEqsl(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: UUID
    ) = sync.syncEqsl(principal, id)

    private fun LogbookWriteRequest.toDbRequest() = DbConfigRequest(
        host = host,
        port = port,
        database = database,
        username = username,
        password = password,
        sslEnabled = sslEnabled
    )
}
