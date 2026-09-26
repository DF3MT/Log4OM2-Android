package com.log4om.api.security

import com.log4om.api.config.Log4omProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SecretBoxTest {
    @Test
    fun roundTrip() {
        val box = SecretBox(Log4omProperties(encryption = Log4omProperties.Encryption("0123456789abcdef0123456789abcdef")))
        val cipher = box.encrypt("s3cret!")
        assertEquals("s3cret!", box.decrypt(cipher))
    }

    @Test
    fun emptyStaysEmpty() {
        val box = SecretBox(Log4omProperties())
        assertEquals("", box.encrypt(""))
        assertEquals("", box.decrypt(""))
    }
}
