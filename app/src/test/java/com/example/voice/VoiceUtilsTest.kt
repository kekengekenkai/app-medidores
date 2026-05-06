package com.example.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class VoiceUtilsTest {
    @Test
    fun testParseDigitsSimple() {
        val digits = VoiceUtils.parseDigitsFromText("uno dos tres")
        assertNotNull(digits)
        assertEquals("123", digits)
    }

    @Test
    fun testParseDigitsComplex() {
        val digits = VoiceUtils.parseDigitsFromText("doce cero cero cuarenta")
        assertNotNull(digits)
        assertEquals("120040", digits)
    }

    @Test
    fun testParseNumberFromDigits() {
        val digits = VoiceUtils.parseDigitsFromText("doce cero cero cuarenta")
        val number = digits?.toDoubleOrNull()
        assertNotNull(number)
        assertEquals(120040.0, number, 0.0001)
    }
}
