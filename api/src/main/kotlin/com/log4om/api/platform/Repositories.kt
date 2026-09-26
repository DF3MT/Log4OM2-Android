package com.log4om.api.platform

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<UserEntity, UUID> {
    fun findByEmailIgnoreCase(email: String): UserEntity?
    fun existsByEmailIgnoreCase(email: String): Boolean
}

interface TenantRepository : JpaRepository<TenantEntity, UUID> {
    fun findByOwnerUserId(ownerUserId: UUID): TenantEntity?
}

interface TenantDbConfigRepository : JpaRepository<TenantDbConfigEntity, UUID>

interface StationProfileRepository : JpaRepository<StationProfileEntity, UUID>

interface LookupCredentialsRepository : JpaRepository<LookupCredentialsEntity, UUID>

interface RefreshTokenRepository : JpaRepository<RefreshTokenEntity, UUID> {
    fun findByTokenHash(tokenHash: String): RefreshTokenEntity?
}
