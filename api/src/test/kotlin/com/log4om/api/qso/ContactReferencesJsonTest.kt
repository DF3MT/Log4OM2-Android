package com.log4om.api.qso

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
        val json = ContactReferencesJson.encode(refs)!!
        val parsed = ContactReferencesJson.parse(json)
        assertEquals("DM/BW-001", parsed.sota)
        assertEquals("EU-005", parsed.iota)
        assertEquals("DE-0001", parsed.pota)
        assertEquals("DLFF-0001", parsed.wwff)
        assertEquals("C-1234", parsed.cota)
    }

    @Test
    fun mergeKeepsOtherAwards() {
        val existing = """[{"Award":"WAIL","Reference":"IT-001"},{"Award":"SOTA","Reference":"OLD"}]"""
        val json = ContactReferencesJson.encode(
            ContactReferencesJson.Refs(sota = "DM/BW-001"),
            existingJson = existing
        )!!
        assertTrue(json.contains("WAIL"))
        assertTrue(json.contains("DM/BW-001"))
        assertTrue(!json.contains("OLD"))
    }
}
