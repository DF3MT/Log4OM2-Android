package com.log4om.api.lookup

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CallbookClientTest {
    @Test
    fun qrzLoginErrorIsProviderText() {
        val http = ScriptedHttp { url ->
            assertTrue(url.contains("username=dl1abc"))
            assertFalse(url.contains("p@ss&word"))
            assertTrue(url.contains("password="))
            """<QRZDatabase><Session><Error>Username/password incorrect</Error></Session></QRZDatabase>"""
        }
        val ex = assertThrows<LookupProviderException> {
            CallbookClient(http).testQrz("dl1abc", "p@ss&word+x", "DF3MT")
        }
        assertEquals("Username/password incorrect", ex.message)
        assertEquals(1, http.urls.size)
    }

    @Test
    fun qrzLookupReturnsNameAndRetriesSessionTimeout() {
        var lookups = 0
        val http = ScriptedHttp { url ->
            when {
                url.contains("username=") -> """<QRZDatabase><Session><Key>session-1</Key></Session></QRZDatabase>"""
                else -> {
                    lookups += 1
                    if (lookups == 1) {
                        """<QRZDatabase><Session><Error>Session Timeout</Error></Session></QRZDatabase>"""
                    } else {
                        """
                        <QRZDatabase xmlns="http://xmldata.qrz.com">
                          <Callsign>
                            <call>DF3MT</call>
                            <fname>Michael</fname>
                            <name>Trunke</name>
                            <country>Germany</country>
                            <grid>JN48</grid>
                          </Callsign>
                        </QRZDatabase>
                        """.trimIndent()
                    }
                }
            }
        }
        val hit = CallbookClient(http).testQrz("user", "secret-pass", "df3mt")
        assertEquals("DF3MT", hit.callsign)
        assertEquals("Michael Trunke", hit.name)
        assertEquals("Germany · JN48", hit.detail)
        assertEquals(2, http.urls.count { it.contains("username=") })
        assertTrue(http.urls.any { it.contains("callsign=DF3MT") && it.contains("s=session-1") })
    }

    @Test
    fun hamqthLoginErrorAndSuccessfulName() {
        val failing = ScriptedHttp {
            """<HamQTH><session><error>Wrong user name or password</error></session></HamQTH>"""
        }
        val ex = assertThrows<LookupProviderException> {
            CallbookClient(failing).testHamqth("op", "nope", "DF3MT")
        }
        assertEquals("Wrong user name or password", ex.message)

        val ok = ScriptedHttp { url ->
            if (url.contains("callsign=")) {
                """
                <HamQTH>
                  <search>
                    <callsign>DF3MT</callsign>
                    <nick>Mike</nick>
                    <adr_name>Michael Trunke</adr_name>
                    <country>Germany</country>
                    <grid>jn48</grid>
                  </search>
                </HamQTH>
                """.trimIndent()
            } else {
                """<HamQTH><session><session_id>abc123</session_id></session></HamQTH>"""
            }
        }
        val hit = CallbookClient(ok).testHamqth("op", "secret-pass", "DF3MT")
        assertEquals("Michael Trunke", hit.name)
        assertEquals("JN48", hit.detail.substringAfter(" · "))
        assertTrue(ok.urls.last().contains("prg=Log4OMAndroid"))
        assertTrue(ok.urls.last().contains("id=abc123"))
    }

    @Test
    fun clubLogPlainTextIsTheErrorAndJsonIsASample() {
        val bad = ScriptedHttp { "Invalid API key" }
        val ex = assertThrows<LookupProviderException> {
            CallbookClient(bad, today = { LocalDate.of(2026, 10, 12) })
                .testClubLog("club-secret-key", "df3mt")
        }
        assertEquals("Invalid API key", ex.message)
        assertTrue(bad.urls.single().contains("call=DF3MT"))
        assertTrue(bad.urls.single().contains("api=club-secret-key"))
        assertTrue(bad.urls.single().contains("year=2026"))
        assertFalse(ex.message!!.contains("club-secret-key"))

        val good = ScriptedHttp {
            """{"DXCC":230,"Name":"FEDERAL REPUBLIC OF GERMANY","Continent":"EU"}"""
        }
        val hit = CallbookClient(good).testClubLog("club-secret-key", "DF3MT")
        assertEquals("FEDERAL REPUBLIC OF GERMANY", hit.name)
        assertEquals("DXCC 230 · EU", hit.detail)
    }

    @Test
    fun passwordInTransportErrorIsRedacted() {
        val http = LookupHttp {
            throw IllegalStateException("boom password=secret-pass url=https://xmldata.qrz.com/?password=secret-pass")
        }
        val ex = assertThrows<LookupProviderException> {
            CallbookClient(http).testQrz("user", "secret-pass", "DF3MT")
        }
        assertFalse(ex.message!!.contains("secret-pass"))
        assertTrue(ex.message!!.contains("***"))
    }

    @Test
    fun missingCallsignIsError() {
        val xml = """<QRZDatabase><Session><Error>Not found: ZZ0ZZ</Error></Session></QRZDatabase>"""
        assertEquals("Not found: ZZ0ZZ", LookupXml.parseQrzCallsign(xml).error)
        assertNull(LookupXml.tag(xml, "Key"))
    }

    @Test
    fun timeoutsAreBounded() {
        assertEquals(10L, LOOKUP_CONNECT_TIMEOUT_SEC)
        assertEquals(15L, LOOKUP_READ_TIMEOUT_SEC)
    }

    private class ScriptedHttp(private val handler: (String) -> String) : LookupHttp {
        val urls = mutableListOf<String>()
        override fun get(url: String): String {
            urls += url
            return handler(url)
        }
    }
}
