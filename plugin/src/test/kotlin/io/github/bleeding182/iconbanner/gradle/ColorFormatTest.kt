package io.github.bleeding182.iconbanner.gradle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ColorFormatTest {

    private fun check(value: String) = ColorFormat.check(value, "color", "devDebug")

    @Test
    fun `hex literals of every accepted length pass through unchanged`() {
        for (value in listOf("#f00", "#8f00", "#FF0000", "#80FF0000")) {
            assertEquals(value, check(value))
        }
    }

    @Test
    fun `resource references pass through unchanged`() {
        for (value in listOf("@color/dev_red", "@android:color/holo_red_dark")) {
            assertEquals(value, check(value))
        }
    }

    @Test
    fun `a theme attribute is refused because a launcher icon has no theme`() {
        // Android accepts ?attr/ in a fillColor, so this cannot be reported as "not a colour": the
        // reason it is useless here is that nothing inflates a launcher icon with a theme, and the
        // user needs to be told that rather than left doubting the syntax.
        for (value in listOf("?attr/colorPrimary", "?colorPrimary")) {
            val failure = assertThrows(IllegalArgumentException::class.java) { check(value) }
            assertEquals(true, failure.message!!.contains("without a theme"), failure.message)
            assertEquals(true, failure.message!!.contains("@color/name"), failure.message)
            assertEquals(true, failure.message!!.contains("devDebug"), failure.message)
        }
    }

    @Test
    fun `malformed literals are rejected with a message naming the property and variant`() {
        val failure = assertThrows(IllegalArgumentException::class.java) { check("#FF00FF00FF") }

        assertEquals(true, failure.message!!.contains("iconBanner.color"))
        assertEquals(true, failure.message!!.contains("devDebug"))
    }

    @Test
    fun `a bare word is not a colour`() {
        assertThrows(IllegalArgumentException::class.java) { check("red") }
        assertThrows(IllegalArgumentException::class.java) { check("#GGGGGG") }
        assertThrows(IllegalArgumentException::class.java) { check("#FF") }
        assertThrows(IllegalArgumentException::class.java) { check("@color") }
    }
}
