package com.log4om.api.tenant

import com.log4om.api.common.BadRequestException
import com.log4om.api.common.NotFoundException
import com.log4om.api.config.Log4omProperties
import com.log4om.api.platform.LogbookEntity
import com.log4om.api.platform.LogbookRepository
import com.log4om.api.platform.TenantRepository
import com.log4om.api.security.SecretBox
import com.log4om.api.security.UserPrincipal
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class DbConfigRequest(
    val host: String,
    val port: Int = 3306,
    val database: String,
    val username: String,
    val password: String? = null,
    val sslEnabled: Boolean = true
)

data class DbConfigResponse(
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val sslEnabled: Boolean,
    val passwordSet: Boolean,
    val lastTestOkAt: Instant?
)

data class DbTestResponse(val ok: Boolean, val message: String)

@Service
class TenantDbService(
    private val logbooks: LogbookRepository,
    private val tenants: TenantRepository,
    private val secretBox: SecretBox,
    private val props: Log4omProperties
) {
    private val pools = ConcurrentHashMap<UUID, HikariDataSource>()

    fun getConfig(principal: UserPrincipal): DbConfigResponse =
        requireActive(principal.tenantId).toDbResponse()

    @Transactional
    fun upsertConfig(principal: UserPrincipal, req: DbConfigRequest): DbConfigResponse {
        if (req.host.isBlank() || req.database.isBlank() || req.username.isBlank()) {
            throw BadRequestException("host, database and username are required")
        }
        val existing = activeOrNull(principal.tenantId)
        val password = when {
            !req.password.isNullOrBlank() -> secretBox.encrypt(req.password)
            existing != null -> existing.encryptedPassword
            else -> throw BadRequestException("password is required on first save")
        }
        val entity = if (existing != null) {
            existing.apply {
                host = req.host.trim()
                port = req.port
                databaseName = req.database.trim()
                username = req.username.trim()
                encryptedPassword = password
                sslEnabled = req.sslEnabled
                updatedAt = Instant.now()
            }
        } else {
            val tenant = tenants.findById(principal.tenantId).orElseThrow {
                NotFoundException("Tenant missing")
            }
            LogbookEntity(
                tenantId = principal.tenantId,
                displayName = tenant.displayName.ifBlank { req.database.trim() },
                stationCallsign = "",
                host = req.host.trim(),
                port = req.port,
                databaseName = req.database.trim(),
                username = req.username.trim(),
                encryptedPassword = password,
                sslEnabled = req.sslEnabled
            )
        }
        val saved = logbooks.save(entity)
        if (existing == null) {
            val tenant = tenants.findById(principal.tenantId).orElseThrow()
            tenant.activeLogbookId = saved.id
            tenants.save(tenant)
        }
        evictPool(saved.id)
        return saved.toDbResponse()
    }

    fun testConnection(principal: UserPrincipal, req: DbConfigRequest? = null): DbTestResponse {
        val existing = activeOrNull(principal.tenantId)
        val probe = if (req != null && req.host.isNotBlank()) {
            probeFrom(existing, req)
        } else {
            val cfg = existing ?: throw NotFoundException("Database not configured")
            DbProbe(
                host = cfg.host,
                port = cfg.port,
                database = cfg.databaseName,
                username = cfg.username,
                password = secretBox.decrypt(cfg.encryptedPassword),
                sslEnabled = cfg.sslEnabled,
                matchesStored = true,
                logbookId = cfg.id
            )
        }
        return runProbe(probe)
    }

    fun testLogbook(book: LogbookEntity, req: DbConfigRequest?): DbTestResponse {
        val probe = if (req != null && req.host.isNotBlank()) {
            probeFrom(book, req)
        } else {
            DbProbe(
                host = book.host,
                port = book.port,
                database = book.databaseName,
                username = book.username,
                password = secretBox.decrypt(book.encryptedPassword),
                sslEnabled = book.sslEnabled,
                matchesStored = true,
                logbookId = book.id
            )
        }
        return runProbe(probe)
    }

    fun <T> withTenantConnection(tenantId: UUID, block: (Connection) -> T): T {
        val book = requireActive(tenantId)
        return withLogbookConnection(book.id, block)
    }

    fun <T> withLogbookConnection(logbookId: UUID, block: (Connection) -> T): T {
        val ds = getOrCreatePool(logbookId)
        return ds.connection.use(block)
    }

    fun requireActive(tenantId: UUID): LogbookEntity =
        activeOrNull(tenantId) ?: throw NotFoundException("Database not configured — set /me/db-config first")

    fun activeOrNull(tenantId: UUID): LogbookEntity? {
        val tenant = tenants.findById(tenantId).orElse(null) ?: return null
        val activeId = tenant.activeLogbookId
        if (activeId != null) {
            logbooks.findById(activeId).orElse(null)?.let { return it }
        }
        return logbooks.findByTenantId(tenantId).minByOrNull { it.createdAt }
    }

    private fun runProbe(probe: DbProbe): DbTestResponse {
        return try {
            DriverManager.getConnection(
                jdbcUrl(probe.host, probe.port, probe.database, probe.sslEnabled),
                probe.username,
                probe.password
            ).use { conn ->
                conn.createStatement().use { st -> st.executeQuery("SELECT 1").close() }
            }
            if (probe.matchesStored && probe.logbookId != null) {
                logbooks.findById(probe.logbookId).ifPresent {
                    it.lastTestOkAt = Instant.now()
                    logbooks.save(it)
                }
            }
            DbTestResponse(true, "Connected")
        } catch (e: Exception) {
            DbTestResponse(false, e.message ?: "Connection failed")
        }
    }

    private fun probeFrom(existing: LogbookEntity?, req: DbConfigRequest): DbProbe {
        if (req.host.isBlank() || req.database.isBlank() || req.username.isBlank()) {
            throw BadRequestException("host, database and username are required")
        }
        val password = when {
            !req.password.isNullOrBlank() -> req.password
            existing != null && existing.encryptedPassword.isNotBlank() ->
                secretBox.decrypt(existing.encryptedPassword)
            else -> throw BadRequestException("password is required on first test")
        }
        val host = req.host.trim()
        val database = req.database.trim()
        val username = req.username.trim()
        val matchesStored = existing != null &&
            req.password.isNullOrBlank() &&
            existing.host == host &&
            existing.port == req.port &&
            existing.databaseName == database &&
            existing.username == username &&
            existing.sslEnabled == req.sslEnabled
        return DbProbe(host, req.port, database, username, password, req.sslEnabled, matchesStored, existing?.id)
    }

    private fun getOrCreatePool(logbookId: UUID): DataSource {
        pools[logbookId]?.takeIf { !it.isClosed }?.let { return it }
        val cfg = logbooks.findById(logbookId).orElseThrow {
            NotFoundException("Database not configured — set /me/db-config first")
        }
        val ds = createPool(cfg)
        pools[logbookId] = ds
        return ds
    }

    private fun createPool(cfg: LogbookEntity): HikariDataSource {
        val password = secretBox.decrypt(cfg.encryptedPassword)
        val hc = HikariConfig().apply {
            jdbcUrl = jdbcUrl(cfg.host, cfg.port, cfg.databaseName, cfg.sslEnabled)
            username = cfg.username
            this.password = password
            maximumPoolSize = props.tenant.poolMaxSize
            idleTimeout = props.tenant.poolIdleTimeoutMs
            connectionTimeout = props.tenant.connectionTimeoutMs
            poolName = "logbook-${cfg.id}"
            isAutoCommit = true
        }
        return HikariDataSource(hc)
    }

    fun evictPool(logbookId: UUID) {
        pools.remove(logbookId)?.close()
    }

    private fun LogbookEntity.toDbResponse() = DbConfigResponse(
        host = host,
        port = port,
        database = databaseName,
        username = username,
        sslEnabled = sslEnabled,
        passwordSet = encryptedPassword.isNotBlank(),
        lastTestOkAt = lastTestOkAt
    )

    private data class DbProbe(
        val host: String,
        val port: Int,
        val database: String,
        val username: String,
        val password: String,
        val sslEnabled: Boolean,
        val matchesStored: Boolean,
        val logbookId: UUID?
    )

    companion object {
        fun jdbcUrl(host: String, port: Int, database: String, sslEnabled: Boolean): String {
            val ssl = if (sslEnabled) "true" else "false"
            return "jdbc:mysql://$host:$port/$database" +
                "?useSSL=$ssl&allowPublicKeyRetrieval=true" +
                "&useUnicode=true&characterEncoding=UTF-8" +
                "&serverTimezone=UTC&connectTimeout=5000&socketTimeout=15000"
        }
    }
}
