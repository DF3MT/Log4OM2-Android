package com.log4om.api.logbook

import com.log4om.api.common.BadRequestException
import com.log4om.api.common.NotFoundException
import com.log4om.api.platform.LogbookEntity
import com.log4om.api.platform.LogbookRepository
import com.log4om.api.platform.TenantRepository
import com.log4om.api.security.SecretBox
import com.log4om.api.security.UserPrincipal
import com.log4om.api.tenant.DbConfigRequest
import com.log4om.api.tenant.DbTestResponse
import com.log4om.api.tenant.TenantDbService
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class LogbookDto(
    val id: UUID,
    val displayName: String,
    val stationCallsign: String,
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val sslEnabled: Boolean,
    val passwordSet: Boolean,
    val qrzLogbookKeySet: Boolean,
    val eqslUser: String,
    val eqslPasswordSet: Boolean,
    val active: Boolean,
    val lastTestOkAt: Instant?
)

data class LogbookWriteRequest(
    val displayName: String = "",
    val stationCallsign: String = "",
    val host: String = "",
    val port: Int = 3306,
    val database: String = "",
    val username: String = "",
    val password: String? = null,
    val sslEnabled: Boolean = true,
    val qrzLogbookKey: String? = null,
    val eqslUser: String? = null,
    val eqslPassword: String? = null
)

@Service
class LogbookService(
    private val logbooks: LogbookRepository,
    private val tenants: TenantRepository,
    private val secretBox: SecretBox,
    private val tenantDb: TenantDbService
) {
    fun list(principal: UserPrincipal): List<LogbookDto> {
        val active = tenants.findById(principal.tenantId).orElse(null)?.activeLogbookId
        return logbooks.findByTenantId(principal.tenantId)
            .sortedWith(compareBy<LogbookEntity> { it.displayName.lowercase() }.thenBy { it.createdAt })
            .map { it.toDto(it.id == active) }
    }

    fun get(principal: UserPrincipal, id: UUID): LogbookDto {
        val book = requireOwned(principal, id)
        val active = tenants.findById(principal.tenantId).orElse(null)?.activeLogbookId
        return book.toDto(book.id == active)
    }

    @Transactional
    fun create(principal: UserPrincipal, req: LogbookWriteRequest): LogbookDto {
        validate(req, passwordRequired = true)
        val password = req.password?.takeIf { it.isNotBlank() }
            ?: throw BadRequestException("password is required")
        val entity = LogbookEntity(
            tenantId = principal.tenantId,
            displayName = req.displayName.trim(),
            stationCallsign = req.stationCallsign.trim().uppercase(),
            host = req.host.trim(),
            port = req.port,
            databaseName = req.database.trim(),
            username = req.username.trim(),
            encryptedPassword = secretBox.encrypt(password),
            sslEnabled = req.sslEnabled,
            encryptedQrzLogbookKey = encryptOrEmpty(req.qrzLogbookKey),
            eqslUser = req.eqslUser.orEmpty().trim(),
            encryptedEqslPassword = encryptOrEmpty(req.eqslPassword)
        )
        val saved = logbooks.save(entity)
        val tenant = tenants.findById(principal.tenantId).orElseThrow {
            NotFoundException("Tenant missing")
        }
        if (tenant.activeLogbookId == null) {
            tenant.activeLogbookId = saved.id
            tenants.save(tenant)
        }
        return saved.toDto(tenant.activeLogbookId == saved.id)
    }

    @Transactional
    fun update(principal: UserPrincipal, id: UUID, req: LogbookWriteRequest): LogbookDto {
        val book = requireOwned(principal, id)
        if (req.displayName.isBlank() || req.host.isBlank() || req.database.isBlank() || req.username.isBlank()) {
            throw BadRequestException("displayName, host, database and username are required")
        }
        book.displayName = req.displayName.trim()
        book.stationCallsign = req.stationCallsign.trim().uppercase()
        book.host = req.host.trim()
        book.port = req.port
        book.databaseName = req.database.trim()
        book.username = req.username.trim()
        book.sslEnabled = req.sslEnabled
        if (!req.password.isNullOrBlank()) book.encryptedPassword = secretBox.encrypt(req.password)
        if (req.qrzLogbookKey != null) book.encryptedQrzLogbookKey = encryptOrEmpty(req.qrzLogbookKey)
        if (req.eqslUser != null) book.eqslUser = req.eqslUser.trim()
        if (req.eqslPassword != null) book.encryptedEqslPassword = encryptOrEmpty(req.eqslPassword)
        book.updatedAt = Instant.now()
        val saved = logbooks.save(book)
        tenantDb.evictPool(saved.id)
        val active = tenants.findById(principal.tenantId).orElse(null)?.activeLogbookId
        return saved.toDto(saved.id == active)
    }

    @Transactional
    fun delete(principal: UserPrincipal, id: UUID) {
        val book = requireOwned(principal, id)
        val tenant = tenants.findById(principal.tenantId).orElseThrow()
        if (tenant.activeLogbookId == id) {
            val replacement = logbooks.findByTenantId(principal.tenantId)
                .filter { it.id != id }
                .minByOrNull { it.createdAt }
            tenant.activeLogbookId = replacement?.id
            tenants.save(tenant)
        }
        logbooks.delete(book)
        tenantDb.evictPool(id)
    }

    @Transactional
    fun activate(principal: UserPrincipal, id: UUID): LogbookDto {
        val book = requireOwned(principal, id)
        val tenant = tenants.findById(principal.tenantId).orElseThrow()
        tenant.activeLogbookId = book.id
        tenants.save(tenant)
        return book.toDto(true)
    }

    fun test(principal: UserPrincipal, id: UUID?, req: DbConfigRequest?): DbTestResponse {
        if (id == null) {
            if (req == null || req.password.isNullOrBlank()) {
                throw BadRequestException("password is required on first test")
            }
            return tenantDb.testConnection(principal, req)
        }
        return tenantDb.testLogbook(requireOwned(principal, id), req)
    }

    fun requireOwned(principal: UserPrincipal, id: UUID): LogbookEntity {
        val book = logbooks.findById(id).orElseThrow { NotFoundException("Logbook not found") }
        if (book.tenantId != principal.tenantId) throw NotFoundException("Logbook not found")
        return book
    }

    fun decryptQrzKey(book: LogbookEntity): String =
        if (book.encryptedQrzLogbookKey.isBlank()) "" else secretBox.decrypt(book.encryptedQrzLogbookKey)

    fun decryptEqslPassword(book: LogbookEntity): String =
        if (book.encryptedEqslPassword.isBlank()) "" else secretBox.decrypt(book.encryptedEqslPassword)

    private fun encryptOrEmpty(value: String?): String {
        val raw = value?.trim().orEmpty()
        return if (raw.isEmpty()) "" else secretBox.encrypt(raw)
    }

    private fun validate(req: LogbookWriteRequest, passwordRequired: Boolean) {
        if (req.displayName.isBlank() || req.host.isBlank() || req.database.isBlank() || req.username.isBlank()) {
            throw BadRequestException("displayName, host, database and username are required")
        }
        if (passwordRequired && req.password.isNullOrBlank()) {
            throw BadRequestException("password is required")
        }
    }

    private fun LogbookEntity.toDto(active: Boolean) = LogbookDto(
        id = id,
        displayName = displayName,
        stationCallsign = stationCallsign,
        host = host,
        port = port,
        database = databaseName,
        username = username,
        sslEnabled = sslEnabled,
        passwordSet = encryptedPassword.isNotBlank(),
        qrzLogbookKeySet = encryptedQrzLogbookKey.isNotBlank(),
        eqslUser = eqslUser,
        eqslPasswordSet = encryptedEqslPassword.isNotBlank(),
        active = active,
        lastTestOkAt = lastTestOkAt
    )
}
