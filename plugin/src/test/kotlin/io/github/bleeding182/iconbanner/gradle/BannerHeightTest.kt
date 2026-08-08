package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.api.BannerCorner
import io.github.bleeding182.iconbanner.generator.Ribbon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BannerHeightTest {

    private fun check(value: Int) = BannerHeight.check(value, "devDebug")

    @Test
    fun `usable heights pass through unchanged`() {
        for (value in listOf(BannerHeight.MIN, 10, BannerDefaults.HEIGHT, BannerHeight.MAX)) {
            assertEquals(value, check(value))
        }
    }

    @Test
    fun `zero and negative heights are rejected with the variant and the range`() {
        for (value in listOf(0, -1, -20)) {
            val failure = assertThrows(IllegalArgumentException::class.java) { check(value) }
            assertTrue(failure.message!!.contains("iconBanner.height"), failure.message)
            assertTrue(failure.message!!.contains("devDebug"), failure.message)
            assertTrue(failure.message!!.contains("${BannerHeight.MIN}..${BannerHeight.MAX}"), failure.message)
        }
    }

    @Test
    fun `heights past the geometry are rejected`() {
        for (value in listOf(BannerHeight.MAX + 1, 100, 1000)) {
            val failure = assertThrows(IllegalArgumentException::class.java) { check(value) }
            assertTrue(failure.message!!.contains("$value"), failure.message)
            assertTrue(failure.message!!.contains("${BannerHeight.MIN}..${BannerHeight.MAX}"), failure.message)
        }
    }

    @Test
    fun `the upper bound is the last height the corner-side anchoring survives`() {
        // BannerHeight.MAX is a plain number in the Gradle layer, deliberately, so the geometry stays
        // out of it. This is what keeps the two honest: the bound is exactly the point where
        // Ribbon.reach hits the icon edge and stops being able to grow inwards.
        fun cornerSideEdge(heightPercent: Int): Double {
            val ribbon = Ribbon(108.0, 108.0, BannerCorner.TOP_LEFT, heightPercent.toDouble())
            return ribbon.reach - ribbon.bandWidth
        }

        val anchored = Ribbon.CORNER_EDGE_FRACTION * 108.0
        assertEquals(anchored, cornerSideEdge(BannerHeight.MAX), 1e-9)
        assertTrue(
            cornerSideEdge(BannerHeight.MAX + 1) < anchored,
            "the anchoring already inverts at ${BannerHeight.MAX + 1}, so MAX is too high",
        )
    }

    @Test
    fun `text still fits at the upper bound`() {
        // The other half of the bound: above it availableTextLength eventually goes negative and the
        // banner becomes a wedge with no text at all and no error.
        val ribbon = Ribbon(108.0, 108.0, BannerCorner.TOP_LEFT, BannerHeight.MAX.toDouble())
        assertTrue(ribbon.availableTextLength > 0.0, "no room for text at ${BannerHeight.MAX}")
        assertTrue(ribbon.availableTextHeight > 0.0)
    }
}
