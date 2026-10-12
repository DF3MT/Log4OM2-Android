package com.log4om.api.lookup

import com.log4om.api.config.Log4omProperties
import com.log4om.api.platform.LookupCredentialsEntity
import com.log4om.api.security.SecretBox
import com.log4om.api.security.UserPrincipal
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LookupCredentialTestServiceTest {
    private val userId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val principal = UserPrincipal(userId, UUID.randomUUID(), "op@example.com")
    private val box = SecretBox(Log4omProperties())

    @Test
    fun typedPasswordIsUsedAndStoredSecretIsNotRequired() {
        val probe = RecordingProbe()
        val store = FakeStore(
            LookupCredentialsEntity(userId = userId).apply {
                qrzUser = "stored-user"
                encryptedQrzPassword = box.encrypt("stored-pass")
            }
        )
        val svc = LookupCredentialTestService(store, box, probe)
        val res = svc.test(
            principal,
            LookupTestRequest(provider = "QRZ", qrzUser = "form-user", qrzPassword = "typed-pass")
        )
        assertTrue(res.ok)
        assertEquals("OK: DF3MT · Michael Trunke · Germany", res.message)
        assertEquals(Triple("form-user", "typed-pass", "DF3MT"), probe.qrz)
    }

    @Test
    fun blankPasswordFallsBackToStoredSecretWithoutSaving() {
        val probe = RecordingProbe()
        val store = FakeStore(
            LookupCredentialsEntity(userId = userId).apply {
                qrzUser = "stored-user"
                encryptedQrzPassword = box.encrypt("stored-pass")
            },
            callsign = "dl1abc"
        )
        val svc = LookupCredentialTestService(store, box, probe)
        val res = svc.test(
            principal,
            LookupTestRequest(provider = "qrz", qrzUser = "form-user", qrzPassword = "  ")
        )
        assertTrue(res.ok)
        assertEquals(Triple("form-user", "stored-pass", "DL1ABC"), probe.qrz)
    }

    @Test
    fun emptyCredentialsReturnAClearErrorAndDoNotCallTheProvider() {
        val probe = RecordingProbe()
        val svc = LookupCredentialTestService(FakeStore(), box, probe)
        val qrz = svc.test(principal, LookupTestRequest(provider = "qrz", qrzUser = "", qrzPassword = null))
        val ham = svc.test(principal, LookupTestRequest(provider = "hamqth", hamqthUser = "only-user"))
        val club = svc.test(principal, LookupTestRequest(provider = "clublog", clublogApiKey = ""))
        assertFalse(qrz.ok)
        assertEquals("QRZ username or password is missing", qrz.message)
        assertEquals("HamQTH username or password is missing", ham.message)
        assertEquals("Club Log API key is missing", club.message)
        assertEquals(null, probe.qrz)
        assertEquals(null, probe.ham)
        assertEquals(null, probe.club)
    }

    @Test
    fun providerErrorIsReturnedAndSecretIsRedacted() {
        val probe = RecordingProbe().apply {
            error = "Username/password incorrect for typed-pass"
        }
        val svc = LookupCredentialTestService(FakeStore(), box, probe)
        val res = svc.test(
            principal,
            LookupTestRequest(provider = "hamqth", hamqthUser = "op", hamqthPassword = "typed-pass")
        )
        assertFalse(res.ok)
        assertEquals("Username/password incorrect for ***", res.message)
        assertFalse(res.message.contains("typed-pass"))
    }

    @Test
    fun clubLogUsesTypedKey() {
        val probe = RecordingProbe()
        val svc = LookupCredentialTestService(FakeStore(), box, probe)
        val res = svc.test(
            principal,
            LookupTestRequest(provider = "clublog", clublogApiKey = "fresh-key")
        )
        assertTrue(res.ok)
        assertEquals("fresh-key" to "DF3MT", probe.club)
        assertTrue(res.message.contains("FEDERAL REPUBLIC OF GERMANY"))
    }

    private class FakeStore(
        var creds: LookupCredentialsEntity? = null,
        var callsign: String? = null
    ) : LookupCredentialStore {
        override fun find(userId: UUID) = creds
        override fun stationCallsign(userId: UUID) = callsign
    }

    private class RecordingProbe : CallbookProbe {
        var qrz: Triple<String, String, String>? = null
        var ham: Triple<String, String, String>? = null
        var club: Pair<String, String>? = null
        var error: String? = null

        override fun testQrz(user: String, password: String, callsign: String): LookupHit {
            qrz = Triple(user, password, callsign)
            error?.let { throw LookupProviderException(it) }
            return LookupHit("DF3MT", "Michael Trunke", "Germany")
        }

        override fun testHamqth(user: String, password: String, callsign: String): LookupHit {
            ham = Triple(user, password, callsign)
            error?.let { throw LookupProviderException(it) }
            return LookupHit(callsign, "Michael Trunke", "Germany")
        }

        override fun testClubLog(apiKey: String, callsign: String): LookupHit {
            club = apiKey to callsign
            error?.let { throw LookupProviderException(it) }
            return LookupHit(callsign, "FEDERAL REPUBLIC OF GERMANY", "DXCC 230")
        }
    }
}
