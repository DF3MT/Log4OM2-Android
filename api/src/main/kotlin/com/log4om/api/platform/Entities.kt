package com.log4om.api.platform

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class UserEntity(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true)
    var email: String = "",
    @Column(name = "password_hash", nullable = false)
    var passwordHash: String = "",
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "tenants")
class TenantEntity(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "owner_user_id", nullable = false)
    val ownerUserId: UUID = UUID.randomUUID(),
    @Column(name = "display_name", nullable = false)
    var displayName: String = "",
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "tenant_db_configs")
class TenantDbConfigEntity(
    @Id
    @Column(name = "tenant_id")
    val tenantId: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    var host: String = "",
    @Column(nullable = false)
    var port: Int = 3306,
    @Column(name = "database_name", nullable = false)
    var databaseName: String = "",
    @Column(nullable = false)
    var username: String = "",
    @Column(name = "encrypted_password", nullable = false)
    var encryptedPassword: String = "",
    @Column(name = "ssl_enabled", nullable = false)
    var sslEnabled: Boolean = true,
    @Column(name = "last_test_ok_at")
    var lastTestOkAt: Instant? = null,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

@Entity
@Table(name = "station_profiles")
class StationProfileEntity(
    @Id
    @Column(name = "user_id")
    val userId: UUID = UUID.randomUUID(),
    var callsign: String = "",
    var gridsquare: String = "",
    var name: String = "",
    var rig: String = "",
    var dxcc: String = "",
    @Column(name = "default_rst_sent")
    var defaultRstSent: String = "59",
    @Column(name = "default_rst_rcvd")
    var defaultRstRcvd: String = "59",
    @Column(name = "default_band")
    var defaultBand: String = "20m",
    @Column(name = "default_mode")
    var defaultMode: String = "SSB",
    @Column(name = "default_txpwr")
    var defaultTxpwr: String = "",
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

@Entity
@Table(name = "lookup_credentials")
class LookupCredentialsEntity(
    @Id
    @Column(name = "user_id")
    val userId: UUID = UUID.randomUUID(),
    @Column(name = "qrz_user")
    var qrzUser: String = "",
    @Column(name = "encrypted_qrz_password")
    var encryptedQrzPassword: String = "",
    @Column(name = "hamqth_user")
    var hamqthUser: String = "",
    @Column(name = "encrypted_hamqth_password")
    var encryptedHamqthPassword: String = "",
    @Column(name = "encrypted_clublog_api_key")
    var encryptedClublogApiKey: String = "",
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

@Entity
@Table(name = "refresh_tokens")
class RefreshTokenEntity(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false)
    val userId: UUID = UUID.randomUUID(),
    @Column(name = "token_hash", nullable = false, unique = true)
    val tokenHash: String = "",
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant = Instant.now(),
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "revoked_at")
    var revokedAt: Instant? = null
)
