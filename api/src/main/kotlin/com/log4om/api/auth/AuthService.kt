package com.log4om.api.auth

import com.log4om.api.common.BadRequestException
import com.log4om.api.common.ConflictException
import com.log4om.api.common.UnauthorizedException
import com.log4om.api.config.Log4omProperties
import com.log4om.api.platform.RefreshTokenEntity
import com.log4om.api.platform.RefreshTokenRepository
import com.log4om.api.platform.StationProfileEntity
import com.log4om.api.platform.StationProfileRepository
import com.log4om.api.platform.TenantEntity
import com.log4om.api.platform.TenantRepository
import com.log4om.api.platform.UserEntity
import com.log4om.api.platform.UserRepository
import com.log4om.api.security.JwtService
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val users: UserRepository,
    private val tenants: TenantRepository,
    private val stations: StationProfileRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val props: Log4omProperties
) {
    @Transactional
    fun register(req: RegisterRequest): TokenResponse {
        val email = req.email.trim().lowercase()
        if (users.existsByEmailIgnoreCase(email)) {
            throw ConflictException("Email already registered")
        }
        val user = users.save(
            UserEntity(
                email = email,
                passwordHash = passwordEncoder.encode(req.password)
            )
        )
        val tenant = tenants.save(
            TenantEntity(
                ownerUserId = user.id,
                displayName = req.displayName?.takeIf { it.isNotBlank() } ?: email.substringBefore("@")
            )
        )
        stations.save(StationProfileEntity(userId = user.id))
        return issueTokens(user, tenant)
    }

    @Transactional
    fun login(req: LoginRequest): TokenResponse {
        val email = req.email.trim().lowercase()
        val user = users.findByEmailIgnoreCase(email)
            ?: throw UnauthorizedException("Invalid credentials")
        if (!passwordEncoder.matches(req.password, user.passwordHash)) {
            throw UnauthorizedException("Invalid credentials")
        }
        val tenant = tenants.findByOwnerUserId(user.id)
            ?: throw BadRequestException("Tenant missing for user")
        return issueTokens(user, tenant)
    }

    @Transactional
    fun refresh(req: RefreshRequest): TokenResponse {
        val hash = hashToken(req.refreshToken)
        val stored = refreshTokens.findByTokenHash(hash)
            ?: throw UnauthorizedException("Invalid refresh token")
        if (stored.revokedAt != null || stored.expiresAt.isBefore(Instant.now())) {
            throw UnauthorizedException("Refresh token expired or revoked")
        }
        stored.revokedAt = Instant.now()
        refreshTokens.save(stored)
        val user = users.findById(stored.userId).orElseThrow { UnauthorizedException("User gone") }
        val tenant = tenants.findByOwnerUserId(user.id)
            ?: throw BadRequestException("Tenant missing for user")
        return issueTokens(user, tenant)
    }

    @Transactional
    fun logout(refreshToken: String) {
        val hash = hashToken(refreshToken)
        refreshTokens.findByTokenHash(hash)?.let {
            it.revokedAt = Instant.now()
            refreshTokens.save(it)
        }
    }

    private fun issueTokens(user: UserEntity, tenant: TenantEntity): TokenResponse {
        val access = jwtService.createAccessToken(user.id, tenant.id, user.email)
        val refresh = generateRefreshToken()
        refreshTokens.save(
            RefreshTokenEntity(
                userId = user.id,
                tokenHash = hashToken(refresh),
                expiresAt = Instant.now().plusSeconds(props.jwt.refreshTokenDays * 24 * 3600)
            )
        )
        return TokenResponse(
            accessToken = access,
            refreshToken = refresh,
            expiresInSeconds = props.jwt.accessTokenMinutes * 60,
            userId = user.id.toString(),
            tenantId = tenant.id.toString(),
            email = user.email
        )
    }

    private fun generateRefreshToken(): String {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
