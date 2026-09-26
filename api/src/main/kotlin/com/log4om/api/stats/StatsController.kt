package com.log4om.api.stats

import com.log4om.api.qso.QsoService
import com.log4om.api.security.UserPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/stats")
class StatsController(
    private val qsoService: QsoService
) {
    @GetMapping("/worked-dxcc")
    fun workedDxcc(@AuthenticationPrincipal principal: UserPrincipal) =
        qsoService.workedDxcc(principal)

    @GetMapping("/worked-dxcc-bands")
    fun workedDxccBands(@AuthenticationPrincipal principal: UserPrincipal) =
        qsoService.workedDxccBands(principal)
}
