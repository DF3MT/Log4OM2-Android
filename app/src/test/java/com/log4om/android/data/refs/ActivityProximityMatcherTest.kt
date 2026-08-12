package com.log4om.android.data.refs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityProximityMatcherTest {

    @Test
    fun radiusHitWithinDefaultSota() {
        val summit = ActivityRef(
            program = ActivityProgram.SOTA,
            reference = "DM/BW-001",
            name = "Test",
            lat = 48.0,
            lon = 9.0
        )
        val hit = ActivityProximityMatcher.matchOne(
            lat = 48.0005,
            lon = 9.0,
            ref = summit,
            radii = ActivityRadii()
        )
        assertNotNull(hit)
        assertEquals(MatchMethod.RADIUS, hit!!.method)
        assertTrue(hit.distanceM < 200)
    }

    @Test
    fun radiusMissOutside() {
        val summit = ActivityRef(
            program = ActivityProgram.SOTA,
            reference = "DM/BW-001",
            name = "Test",
            lat = 48.0,
            lon = 9.0
        )
        val hit = ActivityProximityMatcher.matchOne(
            lat = 48.01,
            lon = 9.0,
            ref = summit,
            radii = ActivityRadii(sotaM = 200)
        )
        assertNull(hit)
    }

    @Test
    fun bboxHit() {
        val iota = ActivityRef(
            program = ActivityProgram.IOTA,
            reference = "EU-001",
            name = "Island",
            lat = 54.5,
            lon = 13.5,
            bbox = doubleArrayOf(54.0, 13.0, 55.0, 14.0)
        )
        val hit = ActivityProximityMatcher.matchOne(
            54.4, 13.4, iota, ActivityRadii()
        )
        assertNotNull(hit)
        assertEquals(MatchMethod.BBOX, hit!!.method)
    }

    @Test
    fun csvQuotedComma() {
        val cols = CsvLineParser.split("""a,"b,c",d""")
        assertEquals(listOf("a", "b,c", "d"), cols)
    }
}
