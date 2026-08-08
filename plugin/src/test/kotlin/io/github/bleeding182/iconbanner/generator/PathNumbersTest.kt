package io.github.bleeding182.iconbanner.generator

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PathNumbersTest {

    private val original = Locale.getDefault()

    @AfterEach
    fun restoreLocale() {
        Locale.setDefault(original)
    }

    @Test
    fun `rounds to two decimals`() {
        assertEquals("23.46", PathNumbers.format(23.4567))
        assertEquals("23.45", PathNumbers.format(23.4549))
    }

    @Test
    fun `strips trailing zeros`() {
        assertEquals("23.1", PathNumbers.format(23.10))
        assertEquals("23", PathNumbers.format(23.0))
        assertEquals("0", PathNumbers.format(0.0))
    }

    @Test
    fun `never emits a negative zero`() {
        // "-0" is legal pathData but makes identical geometry look different.
        assertEquals("0", PathNumbers.format(-0.0))
        assertEquals("0", PathNumbers.format(-0.0001))
    }

    @Test
    fun `keeps the sign of real negatives`() {
        assertEquals("-12.5", PathNumbers.format(-12.5))
    }

    @Test
    fun `uses a dot regardless of the default locale`() {
        // Why this class exists: a comma decimal turns one coordinate into two.
        Locale.setDefault(Locale.forLanguageTag("de-AT"))
        assertEquals("23.1", PathNumbers.format(23.1))
        assertEquals("0.75", PathNumbers.format(0.75))
    }

    @Test
    fun `never uses scientific notation`() {
        assertEquals("0", PathNumbers.format(1e-9))
        assertEquals("10000000", PathNumbers.format(1e7))
    }

    @Test
    fun `rejects non-finite values rather than writing NaN into pathData`() {
        assertFailsWith<IllegalArgumentException> { PathNumbers.format(Double.NaN) }
        assertFailsWith<IllegalArgumentException> { PathNumbers.format(Double.POSITIVE_INFINITY) }
    }
}
