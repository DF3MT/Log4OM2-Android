package com.log4om.api.qso

import com.log4om.api.security.UserPrincipal
import java.time.LocalDate
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/qsos")
class QsoController(
    private val qsoService: QsoService
) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(required = false) callsign: String?,
        @RequestParam(required = false) band: String?,
        @RequestParam(required = false) mode: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateFrom: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateTo: LocalDate?,
        @RequestParam(required = false) country: String?,
        @RequestParam(required = false) dxcc: String?,
        @RequestParam(required = false) sotaRef: String?,
        @RequestParam(required = false) iota: String?,
        @RequestParam(required = false) potaRef: String?,
        @RequestParam(required = false) wwffRef: String?,
        @RequestParam(required = false) cotaRef: String?
    ) = qsoService.list(
        principal,
        LogFilterDto(
            callsign = callsign.orEmpty(),
            band = band.orEmpty(),
            mode = mode.orEmpty(),
            dateFrom = dateFrom,
            dateTo = dateTo,
            country = country.orEmpty(),
            dxcc = dxcc.orEmpty(),
            sotaRef = sotaRef.orEmpty(),
            iota = iota.orEmpty(),
            potaRef = potaRef.orEmpty(),
            wwffRef = wwffRef.orEmpty(),
            cotaRef = cotaRef.orEmpty()
        ),
        limit.coerceIn(1, 500),
        offset.coerceAtLeast(0)
    )

    @GetMapping("/count")
    fun count(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) callsign: String?,
        @RequestParam(required = false) band: String?,
        @RequestParam(required = false) mode: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateFrom: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateTo: LocalDate?,
        @RequestParam(required = false) country: String?,
        @RequestParam(required = false) dxcc: String?,
        @RequestParam(required = false) sotaRef: String?,
        @RequestParam(required = false) iota: String?,
        @RequestParam(required = false) potaRef: String?,
        @RequestParam(required = false) wwffRef: String?,
        @RequestParam(required = false) cotaRef: String?
    ): Map<String, Int> {
        val n = qsoService.count(
            principal,
            LogFilterDto(
                callsign = callsign.orEmpty(),
                band = band.orEmpty(),
                mode = mode.orEmpty(),
                dateFrom = dateFrom,
                dateTo = dateTo,
                country = country.orEmpty(),
                dxcc = dxcc.orEmpty(),
                sotaRef = sotaRef.orEmpty(),
                iota = iota.orEmpty(),
                potaRef = potaRef.orEmpty(),
                wwffRef = wwffRef.orEmpty(),
                cotaRef = cotaRef.orEmpty()
            )
        )
        return mapOf("count" to n)
    }

    @GetMapping("/ids")
    fun ids(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) callsign: String?,
        @RequestParam(required = false) band: String?,
        @RequestParam(required = false) mode: String?
    ) = qsoService.ids(
        principal,
        LogFilterDto(callsign = callsign.orEmpty(), band = band.orEmpty(), mode = mode.orEmpty())
    )

    @PostMapping("/by-ids")
    fun byIds(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody body: IdsRequest
    ) = qsoService.byIds(principal, body.ids)

    @GetMapping("/by-callsign")
    fun byCallsign(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam call: String,
        @RequestParam(defaultValue = "15") limit: Int
    ) = qsoService.byCallsign(principal, call, limit.coerceIn(1, 50))

    @GetMapping("/{id}")
    fun get(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long
    ) = qsoService.get(principal, id)

    @PostMapping
    fun create(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody body: QsoDto
    ) = qsoService.create(principal, body)

    @PutMapping("/{id}")
    fun update(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
        @RequestBody body: QsoDto
    ) = qsoService.update(principal, id, body)

    @DeleteMapping("/{id}")
    fun delete(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long
    ) {
        qsoService.delete(principal, id)
    }
}
