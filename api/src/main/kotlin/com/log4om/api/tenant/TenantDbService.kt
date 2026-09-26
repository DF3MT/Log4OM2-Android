package com.log4om.api.tenant

import com.log4om.api.common.BadRequestException
import com.log4om.api.common.NotFoundException
import com.log4om.api.config.Log4omProperties
import com.log4om.api.platform.TenantDbConfigEntity
import com.log4om.api.platform.TenantDbConfigRepository
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
    private val configs: TenantDbConfigRepository,
    private val secretBox: SecretBox,
    private val props: Log4omProperties
) {
    private val pools = ConcurrentHashMap<UUID, HikariDataSource>()

    fun getConfig(principal: UserPrincipal): DbConfigResponse {
        val cfg = configs.findById(principal.tenantId).orElse(null)
            ?: throw NotFoundException("Database not configured")
        return cfg.toResponse()
    }

    fun upsertConfig(principal: UserPrincipal, req: DbConfigRequest): DbConfigResponse {
        if (req.host.isBlank() || req.database.isBlank() || req.username.isBlank()) {
            throw BadRequestException("host, database and username are required")
        }
        val existing = configs.findById(principal.tenantId).orElse(null)
        val password = when {
            !req.password.isNullOrBlank() -> secretBox.encrypt(req.password)
            existing != null -> existing.encryptedPassword
            else -> throw BadRequestException("password is required on first save")
        }
        val entity = existing?.apply {
            host = req.host.trim()
            port = req.port
            databaseName = req.database.trim()
            username = req.username.trim()
            encryptedPassword = password
            sslEnabled = req.sslEnabled
            updatedAt = Instant.now()
        } ?: TenantDbConfigEntity(
            tenantId = principal.tenantId,
            host = req.host.trim(),
            port = req.port,
            databaseName = req.database.trim(),
            username = req.username.trim(),
            encryptedPassword = password,
            sslEnabled = req.sslEnabled
        )
        configs.save(entity)
        evictPool(principal.tenantId)
        return entity.toResponse()
    }

    fun testConnection(principal: UserPrincipal): DbTestResponse {
        val cfg = configs.findById(principal.tenantId).orElse(null)
            ?: throw NotFoundException("Database not configured")
        return try {
            DriverManager.getConnection(
                buildJdbcUrl(cfg),
                cfg.username,
                secretBox.decrypt(cfg.encryptedPassword)
            ).use { conn ->
                conn.createStatement().use { st -> st.executeQuery("SELECT 1").close() }
            }
            cfg.lastTestOkAt = Instant.now()
            configs.save(cfg)
            DbTestResponse(true, "Connected")
        } catch (e: Exception) {
            DbTestResponse(false, e.message ?: "Connection failed")
        }
    }

    fun <T> withTenantConnection(tenantId: UUID, block: (Connection) -> T): T {
        val ds = getOrCreatePool(tenantId)
        return ds.connection.use(block)
    }

    private fun getOrCreatePool(tenantId: UUID): DataSource {
        pools[tenantId]?.takeIf { !it.isClosed }?.let { return it }
        val cfg = configs.findById(tenantId).orElseThrow {
            NotFoundException("Database not configured — set /me/db-config first")
        }
        val ds = createPool(cfg)
        pools[tenantId] = ds
        return ds
    }

    private fun createPool(cfg: TenantDbConfigEntity): HikariDataSource {
        val password = secretBox.decrypt(cfg.encryptedPassword)
        val hc = HikariConfig().apply {
            jdbcUrl = buildJdbcUrl(cfg)
            username = cfg.username
            this.password = password
            maximumPoolSize = props.tenant.poolMaxSize
            idleTimeout = props.tenant.poolIdleTimeoutMs
            connectionTimeout = props.tenant.connectionTimeoutMs
            poolName = "tenant-${cfg.tenantId}"
            isAutoCommit = true
        }
        return HikariDataSource(hc)
    }

    fun evictPool(tenantId: UUID) {
        pools.remove(tenantId)?.close()
    }

    private fun TenantDbConfigEntity.toResponse() = DbConfigResponse(
        host = host,
        port = port,
        database = databaseName,
        username = username,
        sslEnabled = sslEnabled,
        passwordSet = encryptedPassword.isNotBlank(),
        lastTestOkAt = lastTestOkAt
    )

    companion object {
        fun buildJdbcUrl(cfg: TenantDbConfigEntity): String {
            val ssl = if (cfg.sslEnabled) "true" else "false"
            return "jdbc:mysql://${cfg.host}:${cfg.port}/${cfg.databaseName}" +
                "?useSSL=$ssl&allowPublicKeyRetrieval=true" +
                "&useUnicode=true&characterEncoding=UTF-8" +
                "&serverTimezone=UTC&connectTimeout=5000&socketTimeout=15000"
        }
    }
}
