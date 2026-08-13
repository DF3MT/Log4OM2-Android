package com.log4om.android.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactReferencesJsonTest {

    @Test
    fun emptyRefsEncodeToNull() {
        assertNull(ContactReferencesJson.encode(ContactReferencesJson.Refs()))
        assertNull(ContactReferencesJson.encode(ContactReferencesJson.Refs(sota = "  ")))
    }

    @Test
    fun filledRefsRoundTrip() {
        val refs = ContactReferencesJson.Refs(
            sota = "DM/BW-001",
            iota = "EU-005",
            pota = "DE-0001",
            wwff = "DLFF-0001",
            cota = "C-1234"
        )
        val json = ContactReferencesJson.encode(refs)
        val parsed = ContactReferencesJson.parse(json)
        assertEquals("DM/BW-001", parsed.sota)
        assertEquals("EU-005", parsed.iota)
        assertEquals("DE-0001", parsed.pota)
        assertEquals("DLFF-0001", parsed.wwff)
        assertEquals("C-1234", parsed.cota)
    }

    @Test
    fun parseNullAndEmpty() {
        assertEquals(ContactReferencesJson.Refs(), ContactReferencesJson.parse(null))
        assertEquals(ContactReferencesJson.Refs(), ContactReferencesJson.parse(""))
        assertEquals(ContactReferencesJson.Refs(), ContactReferencesJson.parse("[]"))
    }

    @Test
    fun mergeKeepsOtherAwards() {
        val existing = """[{"Award":"WAIL","Reference":"IT-001"},{"Award":"SOTA","Reference":"OLD"}]"""
        val json = ContactReferencesJson.encode(
            ContactReferencesJson.Refs(sota = "DM/BW-001"),
            existingJson = existing
        )!!
        val parsed = ContactReferencesJson.parse(json)
        assertEquals("DM/BW-001", parsed.sota)
        assertTrue(json.contains("WAIL"))
        assertTrue(json.contains("IT-001"))
        assertTrue(!json.contains("OLD"))
    }

    @Test
    fun parseCamelCaseAndShortKeys() {
        val json = """[{"award":"IOTA","reference":"EU-131"},{"id":"POTA","r":"US-1234"}]"""
        val parsed = ContactReferencesJson.parse(json)
        assertEquals("EU-131", parsed.iota)
        assertEquals("US-1234", parsed.pota)
    }
}
