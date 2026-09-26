package com.log4om.api.security

import com.log4om.api.config.Log4omProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey
import org.springframework.stereotype.Component

data class AccessClaims(
    val userId: UUID,
    val tenantId: UUID,
    val email: String
)

@Component
class JwtService(props: Log4omProperties) {
    private val key: SecretKey = Keys.hmacShaKeyFor(
        props.jwt.secret.toByteArray(StandardCharsets.UTF_8).let { bytes ->
            if (bytes.size >= 32) bytes else MessageDigestHelper.sha256(props.jwt.secret)
        }
    )
    private val accessMinutes = props.jwt.accessTokenMinutes

    fun createAccessToken(userId: UUID, tenantId: UUID, email: String): String {
        val now = Instant.now()
        return Jwts.builder()
            .subject(userId.toString())
            .claim("tid", tenantId.toString())
            .claim("email", email)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(accessMinutes * 60)))
            .signWith(key)
            .compact()
    }

    fun parseAccessToken(token: String): AccessClaims {
        val claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
        return AccessClaims(
            userId = UUID.fromString(claims.subject),
            tenantId = UUID.fromString(claims["tid"] as String),
            email = claims["email"] as String
        )
    }
}

private object MessageDigestHelper {
    fun sha256(s: String): ByteArray =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray(StandardCharsets.UTF_8))
}
