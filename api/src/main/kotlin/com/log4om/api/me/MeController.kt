package com.log4om.api.me

import com.log4om.api.platform.LookupCredentialsEntity
import com.log4om.api.platform.LookupCredentialsRepository
import com.log4om.api.platform.StationProfileEntity
import com.log4om.api.platform.StationProfileRepository
import com.log4om.api.security.SecretBox
import com.log4om.api.security.UserPrincipal
import com.log4om.api.tenant.DbConfigRequest
import com.log4om.api.tenant.TenantDbService
import java.time.Instant
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class StationProfileDto(
    val callsign: String = "",
    val gridsquare: String = "",
    val name: String = "",
    val rig: String = "",
    val dxcc: String = "",
    val defaultRstSent: String = "59",
    val defaultRstRcvd: String = "59",
    val defaultBand: String = "20m",
    val defaultMode: String = "SSB",
    val defaultTxpwr: String = ""
)

data class LookupCredentialsDto(
    val qrzUser: String = "",
    val qrzPassword: String? = null,
    val hamqthUser: String = "",
    val hamqthPassword: String? = null,
    val clublogApiKey: String? = null,
    val qrzPasswordSet: Boolean = false,
    val hamqthPasswordSet: Boolean = false,
    val clublogApiKeySet: Boolean = false
)

@RestController
@RequestMapping("/me")
class MeController(
    private val stations: StationProfileRepository,
    private val lookups: LookupCredentialsRepository,
    private val tenantDb: TenantDbService,
    private val secretBox: SecretBox
) {
    @GetMapping("/station")
    fun getStation(@AuthenticationPrincipal principal: UserPrincipal): StationProfileDto {
        val s = stations.findById(principal.userId).orElseGet {
            stations.save(StationProfileEntity(userId = principal.userId))
        }
        return s.toDto()
    }

    @PutMapping("/station")
    fun putStation(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody body: StationProfileDto
    ): StationProfileDto {
        val s = stations.findById(principal.userId).orElse(StationProfileEntity(userId = principal.userId))
        s.callsign = body.callsign.trim().uppercase()
        s.gridsquare = body.gridsquare.trim().uppercase()
        s.name = body.name.trim()
        s.rig = body.rig.trim()
        s.dxcc = body.dxcc.trim()
        s.defaultRstSent = body.defaultRstSent
        s.defaultRstRcvd = body.defaultRstRcvd
        s.defaultBand = body.defaultBand
        s.defaultMode = body.defaultMode
        s.defaultTxpwr = body.defaultTxpwr
        s.updatedAt = Instant.now()
        return stations.save(s).toDto()
    }

    @GetMapping("/db-config")
    fun getDb(@AuthenticationPrincipal principal: UserPrincipal) = tenantDb.getConfig(principal)

    @PutMapping("/db-config")
    fun putDb(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody body: DbConfigRequest
    ) = tenantDb.upsertConfig(principal, body)

    @PostMapping("/db-config/test")
    fun testDb(@AuthenticationPrincipal principal: UserPrincipal) = tenantDb.testConnection(principal)

    @GetMapping("/lookup-credentials")
    fun getLookup(@AuthenticationPrincipal principal: UserPrincipal): LookupCredentialsDto {
        val c = lookups.findById(principal.userId).orElse(null) ?: return LookupCredentialsDto()
        return LookupCredentialsDto(
            qrzUser = c.qrzUser,
            hamqthUser = c.hamqthUser,
            qrzPasswordSet = c.encryptedQrzPassword.isNotBlank(),
            hamqthPasswordSet = c.encryptedHamqthPassword.isNotBlank(),
            clublogApiKeySet = c.encryptedClublogApiKey.isNotBlank()
        )
    }

    @PutMapping("/lookup-credentials")
    fun putLookup(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody body: LookupCredentialsDto
    ): LookupCredentialsDto {
        val c = lookups.findById(principal.userId).orElse(LookupCredentialsEntity(userId = principal.userId))
        c.qrzUser = body.qrzUser.trim()
        c.hamqthUser = body.hamqthUser.trim()
        if (body.qrzPassword != null) c.encryptedQrzPassword = secretBox.encrypt(body.qrzPassword)
        if (body.hamqthPassword != null) c.encryptedHamqthPassword = secretBox.encrypt(body.hamqthPassword)
        if (body.clublogApiKey != null) c.encryptedClublogApiKey = secretBox.encrypt(body.clublogApiKey)
        c.updatedAt = Instant.now()
        lookups.save(c)
        return getLookup(principal)
    }

    private fun StationProfileEntity.toDto() = StationProfileDto(
        callsign, gridsquare, name, rig, dxcc,
        defaultRstSent, defaultRstRcvd, defaultBand, defaultMode, defaultTxpwr
    )
}
