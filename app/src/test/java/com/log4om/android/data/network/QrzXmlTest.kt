package com.log4om.android.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrzXmlTest {

    @Test
    fun loginKeyIgnoresWhitespaceAndNamespace() {
        val xml = """
            <?xml version="1.0"?>
            <QRZDatabase xmlns="http://xmldata.qrz.com" version="1.34">
              <Session>
                <Key>
                  abcDEF123
                </Key>
              </Session>
            </QRZDatabase>
        """.trimIndent()
        assertEquals("abcDEF123", QrzXml.tag(xml, "Key"))
    }

    @Test
    fun loginErrorFromSession() {
        val xml = """
            <QRZDatabase xmlns="http://xmldata.qrz.com">
              <Session><Error>Username/password incorrect</Error></Session>
            </QRZDatabase>
        """.trimIndent()
        assertEquals("Username/password incorrect", QrzXml.tag(xml, "Error"))
        assertNull(QrzXml.tag(xml, "Key"))
    }

    @Test
    fun parseCallsignFromNamespacedXml() {
        val xml = """
            <?xml version="1.0"?>
            <QRZDatabase xmlns="http://xmldata.qrz.com">
              <Session><Key>k</Key></Session>
              <Callsign>
                <call>DL1ABC</call>
                <fname>Michael</fname>
                <name>Test</name>
                <addr2>Berlin</addr2>
                <country>Germany</country>
                <grid>JO62</grid>
                <dxcc>230</dxcc>
                <lat>52.5</lat>
                <lon>13.4</lon>
              </Callsign>
            </QRZDatabase>
        """.trimIndent()
        val data = QrzXml.parseCallsign(xml)
        assertEquals("DL1ABC", data.call)
        assertEquals("Michael Test", data.name)
        assertEquals("Germany", data.country)
        assertEquals("JO62", data.grid)
        assertEquals("230", data.dxcc)
        assertNull(data.error)
        assertTrue(data.hasUsefulData)
    }

    @Test
    fun missingCallsignIsError() {
        val xml = """
            <QRZDatabase><Session><Error>Not found: ZZ0ZZ</Error></Session></QRZDatabase>
        """.trimIndent()
        val data = QrzXml.parseCallsign(xml)
        assertEquals("Not found: ZZ0ZZ", data.error)
        assertTrue(!data.hasUsefulData)
    }

    @Test
    fun loginUrlEncodesSpecialCharsInPassword() {
        val url = QrzXml.loginUrl("dl1abc", "p@ss&word+x")
        assertTrue(url.contains("username=dl1abc"))
        assertTrue(url.contains("password="))
        assertTrue(!url.contains("p@ss&word+x"))
        assertTrue(url.contains("%26") || url.contains("%40"))
    }
}
