package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.api.BannerCorner
import io.github.bleeding182.iconbanner.generator.Ribbon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BannerTextSizeTest {

    private val defaultLineHeight = BannerDefaults.LINE_HEIGHT

    private fun check(maxTextSize: Int, lineHeight: Double = defaultLineHeight) =
        BannerTextSize.check(maxTextSize, lineHeight, "devDebug")

    @Test
    fun `usable text sizes pass`() {
        val values = listOf(BannerTextSize.MIN, 8, BannerDefaults.MAX_TEXT_SIZE)
        for (value in values + BannerTextSize.maxFor(defaultLineHeight)) {
            check(value)
        }
    }

    @Test
    fun `zero and negative text sizes are rejected with the variant and the range`() {
        for (value in listOf(0, -1, -20)) {
            val failure = assertThrows(IllegalArgumentException::class.java) { check(value) }
            assertTrue(failure.message!!.contains("iconBanner.maxTextSize"), failure.message)
            assertTrue(failure.message!!.contains("devDebug"), failure.message)
            assertTrue(
                failure.message!!.contains("${BannerTextSize.MIN}..${BannerTextSize.maxFor(defaultLineHeight)}"),
                failure.message,
            )
        }
    }

    @Test
    fun `text sizes past the geometry are rejected`() {
        val max = BannerTextSize.maxFor(defaultLineHeight)
        for (value in listOf(max + 1, 100, 1000)) {
            val failure = assertThrows(IllegalArgumentException::class.java) { check(value) }
            assertTrue(failure.message!!.contains("$value"), failure.message)
            assertTrue(failure.message!!.contains("${BannerTextSize.MIN}..$max"), failure.message)
        }
    }

    @Test
    fun `the ceiling moves with the line height, because it is the band that is bounded`() {
        // A thicker line spends the same band width on fewer units of text, so the largest usable
        // text size drops. Rejecting the pair rather than either value alone is the only honest
        // check: neither number is out of range on its own.
        assertEquals(20, BannerTextSize.maxFor(1.5))
        assertEquals(30, BannerTextSize.maxFor(1.0))
        assertEquals(10, BannerTextSize.maxFor(3.0))
        check(30, lineHeight = 1.0)
        assertThrows(IllegalArgumentException::class.java) { check(30, lineHeight = 1.5) }
    }

    @Test
    fun `line heights outside the range are rejected`() {
        for (value in listOf(0.0, -1.0, 0.99, 3.01, 40.0)) {
            val failure = assertThrows(IllegalArgumentException::class.java) { check(13, lineHeight = value) }
            assertTrue(failure.message!!.contains("iconBanner.lineHeight"), failure.message)
            assertTrue(failure.message!!.contains("devDebug"), failure.message)
        }
        check(13, lineHeight = BannerTextSize.MIN_LINE_HEIGHT)
        check(10, lineHeight = BannerTextSize.MAX_LINE_HEIGHT)
    }

    @Test
    fun `the band bound is the last one that keeps the ribbon inside the safe zone`() {
        // The bounds here are plain numbers in the Gradle layer, deliberately, so the geometry stays
        // out of it. This is what keeps the two honest: the bound is exactly the widest band whose
        // corner-side edge is still inside the mask's safe zone, past which a launcher renders the
        // band thinner than it was asked to be and says nothing.
        fun cornerSideEdgeOffset(bandPercent: Double): Double {
            val ribbon = Ribbon(108.0, 108.0, BannerCorner.TOP_LEFT, bandPercent, 1.0)
            return (ribbon.s - ribbon.cornerSideEdge) / Math.sqrt(2.0)
        }

        val safeRadius = Ribbon.SAFE_ZONE_FRACTION * 108.0
        assertTrue(
            cornerSideEdgeOffset(BannerTextSize.MAX_BAND_PERCENT) <= safeRadius,
            "a band of ${BannerTextSize.MAX_BAND_PERCENT}% already leaves the safe zone",
        )
        assertTrue(
            cornerSideEdgeOffset(BannerTextSize.MAX_BAND_PERCENT + 1.0) > safeRadius,
            "a band one point wider is still inside the safe zone, so the bound is too low",
        )
    }

    @Test
    fun `text still fits at the widest band`() {
        // The other half of the bound: the band may not eat the length the text needs. It cannot any
        // more — the length budget no longer depends on the band at all — and this pins that.
        val max = BannerTextSize.maxFor(defaultLineHeight)
        val ribbon = Ribbon(108.0, 108.0, BannerCorner.TOP_LEFT, max.toDouble(), defaultLineHeight)
        val thin = Ribbon(108.0, 108.0, BannerCorner.TOP_LEFT, BannerTextSize.MIN.toDouble(), defaultLineHeight)
        assertTrue(ribbon.textLengthBudget > 0.0, "no room for text at $max")
        assertEquals(thin.textLengthBudget, ribbon.textLengthBudget, 1e-9)
    }
}
