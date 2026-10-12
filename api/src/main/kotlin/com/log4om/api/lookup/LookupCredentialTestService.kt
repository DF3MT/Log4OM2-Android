package com.log4om.api.lookup

import com.log4om.api.platform.LookupCredentialsEntity
import com.log4om.api.platform.LookupCredentialsRepository
import com.log4om.api.platform.StationProfileRepository
import com.log4om.api.security.SecretBox
import com.log4om.api.security.UserPrincipal
import java.util.UUID
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

data class LookupTestRequest(
    val provider: String = "",
    val qrzUser: String? = null,
    val qrzPassword: String? = null,
    val hamqthUser: String? = null,
    val hamqthPassword: String? = null,
    val clublogApiKey: String? = null
)

data class LookupTestResponse(
    val ok: Boolean,
    val provider: String,
    val message: String,
    val callsign: String = "",
    val name: String = "",
    val detail: String = ""
)

/**
 * Read-only view of stored lookup secrets. The test path has no save method.
 */
interface LookupCredentialStore {
    fun find(userId: UUID): LookupCredentialsEntity?
    fun stationCallsign(userId: UUID): String?
}

@Component
class JpaLookupCredentialStore(
    private val lookups: LookupCredentialsRepository,
    private val stations: StationProfileRepository
) : LookupCredentialStore {
    override fun find(userId: UUID): LookupCredentialsEntity? =
        lookups.findById(userId).orElse(null)

    override fun stationCallsign(userId: UUID): String? =
        stations.findById(userId).orElse(null)?.callsign
}

@Service
class LookupCredentialTestService(
    private val store: LookupCredentialStore,
    private val secretBox: SecretBox,
    private val probe: CallbookProbe
) {
    fun test(principal: UserPrincipal, body: LookupTestRequest): LookupTestResponse {
        val provider = body.provider.trim().lowercase()
        val stored = store.find(principal.userId)
        val callsign = store.stationCallsign(principal.userId)
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_CALL
        return try {
            when (provider) {
                "qrz" -> testQrz(provider, body, stored, callsign)
                "hamqth" -> testHamqth(provider, body, stored, callsign)
                "clublog" -> testClubLog(provider, body, stored, callsign)
                else -> fail(provider.ifBlank { "unknown" }, "Unknown lookup provider", emptyList())
            }
        } catch (ex: LookupProviderException) {
            fail(provider.ifBlank { "unknown" }, ex.message ?: "Lookup failed", emptyList())
        }
    }

    private fun testQrz(
        provider: String,
        body: LookupTestRequest,
        stored: LookupCredentialsEntity?,
        callsign: String
    ): LookupTestResponse {
        val user = resolveUser(body.qrzUser, stored?.qrzUser)
        val password = resolveSecret(body.qrzPassword, stored?.encryptedQrzPassword)
        if (user.isBlank() || password.isBlank()) {
            return fail(provider, "QRZ username or password is missing", listOf(password))
        }
        return runProbe(provider, listOf(password)) {
            probe.testQrz(user, password, callsign)
        }
    }

    private fun testHamqth(
        provider: String,
        body: LookupTestRequest,
        stored: LookupCredentialsEntity?,
        callsign: String
    ): LookupTestResponse {
        val user = resolveUser(body.hamqthUser, stored?.hamqthUser)
        val password = resolveSecret(body.hamqthPassword, stored?.encryptedHamqthPassword)
        if (user.isBlank() || password.isBlank()) {
            return fail(provider, "HamQTH username or password is missing", listOf(password))
        }
        return runProbe(provider, listOf(password)) {
            probe.testHamqth(user, password, callsign)
        }
    }

    private fun testClubLog(
        provider: String,
        body: LookupTestRequest,
        stored: LookupCredentialsEntity?,
        callsign: String
    ): LookupTestResponse {
        val apiKey = resolveSecret(body.clublogApiKey, stored?.encryptedClublogApiKey)
        if (apiKey.isBlank()) {
            return fail(provider, "Club Log API key is missing", emptyList())
        }
        return runProbe(provider, listOf(apiKey)) {
            probe.testClubLog(apiKey, callsign)
        }
    }

    private fun resolveUser(override: String?, stored: String?): String {
        return if (override != null) override.trim() else stored?.trim().orEmpty()
    }

    private fun resolveSecret(override: String?, encrypted: String?): String {
        val typed = override?.trim().orEmpty()
        if (typed.isNotEmpty()) return typed
        val blob = encrypted?.trim().orEmpty()
        if (blob.isEmpty()) return ""
        return try {
            secretBox.decrypt(blob).trim()
        } catch (_: Exception) {
            throw LookupProviderException("Stored secret could not be read")
        }
    }

    private fun runProbe(provider: String, secrets: List<String>, block: () -> LookupHit): LookupTestResponse {
        return try {
            ok(provider, block())
        } catch (ex: LookupProviderException) {
            fail(provider, ex.message ?: "Lookup failed", secrets)
        } catch (ex: Exception) {
            fail(provider, ex.message ?: "Lookup failed", secrets)
        }
    }

    private fun ok(provider: String, hit: LookupHit): LookupTestResponse {
        val sample = listOf(hit.callsign, hit.name, hit.detail).filter { it.isNotBlank() }.joinToString(" · ")
        return LookupTestResponse(
            ok = true,
            provider = provider,
            message = if (sample.isBlank()) "OK" else "OK: $sample",
            callsign = hit.callsign,
            name = hit.name,
            detail = hit.detail
        )
    }

    private fun fail(provider: String, message: String, secrets: List<String>): LookupTestResponse {
        val text = redactSecrets(message, secrets).ifBlank { "Lookup failed" }
        return LookupTestResponse(ok = false, provider = provider, message = text)
    }

    companion object {
        const val DEFAULT_CALL = "DF3MT"
    }
}
