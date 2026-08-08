package io.github.bleeding182.iconbanner.gradle

import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The version rule only. Reaching it through a real `AndroidPluginVersion` is not possible here — AGP
 * is `compileOnly`, so no AGP type is on the test classpath — which is exactly why
 * [unsupportedAgpMessage] takes the version apart into plain ints.
 */
class AgpVersionTest {

    @Test
    fun `agp 9 3 and newer is accepted`() {
        assertNull(unsupportedAgpMessage(9, 3, 0))
        assertNull(unsupportedAgpMessage(9, 3, 1))
        assertNull(unsupportedAgpMessage(9, 4, 0))
        assertNull(unsupportedAgpMessage(10, 0, 0))
    }

    @Test
    fun `older agp is refused with the minimum and the version found`() {
        val message = assertNotNull(unsupportedAgpMessage(8, 7, 3))

        assertTrue("9.3" in message, message)
        assertTrue("8.7.3" in message, message)
        // Actionable, not just a statement of fact.
        assertTrue("Upgrade AGP" in message, message)
    }

    @Test
    fun `an older minor of the same major is refused`() {
        assertNotNull(unsupportedAgpMessage(9, 2, 9))
        assertNotNull(unsupportedAgpMessage(9, 0, 0))
    }
}
