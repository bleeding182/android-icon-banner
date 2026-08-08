package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.api.BannerCorner
import io.github.bleeding182.iconbanner.generator.Ribbon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BannerGeometryBoundsTest {

    private val defaultLineHeight = BannerDefaults.LINE_HEIGHT

    private fun check(maxTextSize: Int, lineHeight: Double = defaultLineHeight) =
        BannerGeometryBounds.check(maxTextSize, lineHeight, "devDebug")

    @Test
    fun `usable text sizes pass`() {
        val values = listOf(
            BannerGeometryBounds.MIN_TEXT_SIZE,
            8,
            BannerDefaults.MAX_TEXT_SIZE,
            BannerGeometryBounds.MAX_TEXT_SIZE,
        )
        for (value in values) {
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
                failure.message!!.contains(
                    "${BannerGeometryBounds.MIN_TEXT_SIZE}..${BannerGeometryBounds.MAX_TEXT_SIZE}"
                ),
                failure.message,
            )
        }
    }

    @Test
    fun `text sizes past the geometry are rejected`() {
        val max = BannerGeometryBounds.MAX_TEXT_SIZE
        for (value in listOf(max + 1, 100, 1000)) {
            val failure = assertThrows(IllegalArgumentException::class.java) { check(value) }
            assertTrue(failure.message!!.contains("$value"), failure.message)
            assertTrue(
                failure.message!!.contains("${BannerGeometryBounds.MIN_TEXT_SIZE}..$max"),
                failure.message,
            )
        }
    }

    @Test
    fun `the text size range does not move with the line height`() {
        // The two knobs used to be validated as a pair, with maxTextSize capped at 30 / lineHeight.
        // That made the documented default illegal at a lineHeight of 2.4 — a DSL rejecting its own
        // default — and the premise was wrong anyway: thickness costs the text nothing, so it cannot
        // narrow the range of sizes the text may be drawn at.
        for (lineHeight in listOf(1.0, 1.5, 2.4, BannerGeometryBounds.MAX_LINE_HEIGHT)) {
            check(BannerDefaults.MAX_TEXT_SIZE, lineHeight)
            check(BannerGeometryBounds.MIN_TEXT_SIZE, lineHeight)
            check(BannerGeometryBounds.MAX_TEXT_SIZE, lineHeight)
        }
        // And the message says nothing about the line height, because the bound does not depend on it.
        val failure = assertThrows(IllegalArgumentException::class.java) {
            check(BannerGeometryBounds.MAX_TEXT_SIZE + 1, lineHeight = 2.4)
        }
        assertTrue(!failure.message!!.contains("lineHeight"), failure.message)
    }

    @Test
    fun `line heights outside the range are rejected`() {
        for (value in listOf(0.0, -1.0, 0.99, 3.01, 40.0)) {
            val failure = assertThrows(IllegalArgumentException::class.java) { check(13, lineHeight = value) }
            assertTrue(failure.message!!.contains("iconBanner.lineHeight"), failure.message)
            assertTrue(failure.message!!.contains("devDebug"), failure.message)
        }
        check(13, lineHeight = BannerGeometryBounds.MIN_LINE_HEIGHT)
        check(13, lineHeight = BannerGeometryBounds.MAX_LINE_HEIGHT)
    }

    @Test
    fun `the text size bound is the last one that keeps the glyphs inside the safe zone`() {
        // The bounds here are plain numbers in the Gradle layer, deliberately, so the geometry stays
        // out of it. This is what keeps the two honest: the bound is exactly the largest cap height
        // whose corner-side half is still inside the mask's safe zone, past which a launcher cuts
        // into the glyphs themselves and says nothing.
        //
        // Note what is not here: the band. Its thickness may push its own edges out of the safe zone,
        // and the part outside is simply not drawn — only the text has to survive the mask.
        fun glyphEdgeFromCentre(textPercent: Double): Double {
            val ribbon = Ribbon(108.0, 108.0, BannerCorner.TOP_LEFT, textPercent, 1.0)
            return ribbon.perpendicularFromIconCentre(ribbon.centreLineAxis) + ribbon.textSize / 2.0
        }

        val safeRadius = Ribbon.SAFE_ZONE_FRACTION * 108.0
        assertTrue(
            glyphEdgeFromCentre(BannerGeometryBounds.MAX_TEXT_SIZE.toDouble()) <= safeRadius,
            "text at ${BannerGeometryBounds.MAX_TEXT_SIZE}% already leaves the safe zone",
        )
        assertTrue(
            glyphEdgeFromCentre(BannerGeometryBounds.MAX_TEXT_SIZE + 1.0) > safeRadius,
            "text one point larger is still inside the safe zone, so the bound is too low",
        )
    }

    @Test
    fun `the length the text is fitted against is the same at every bound`() {
        // The other half of the bound: neither knob may eat the length the text needs. Neither can —
        // the length budget is the chord across the safe zone at a pinned centre line — and this pins
        // that, for the widest text and the thickest band the ranges allow.
        fun ribbon(textPercent: Int, lineHeight: Double) =
            Ribbon(108.0, 108.0, BannerCorner.TOP_LEFT, textPercent.toDouble(), lineHeight)

        val thin = ribbon(BannerGeometryBounds.MIN_TEXT_SIZE, BannerGeometryBounds.MIN_LINE_HEIGHT)
        val widest = ribbon(BannerGeometryBounds.MAX_TEXT_SIZE, defaultLineHeight)
        val thickest = ribbon(BannerDefaults.MAX_TEXT_SIZE, BannerGeometryBounds.MAX_LINE_HEIGHT)
        assertTrue(thin.textLengthBudget > 0.0, "no room for text at all")
        assertEquals(thin.textLengthBudget, widest.textLengthBudget, 1e-9)
        assertEquals(thin.textLengthBudget, thickest.textLengthBudget, 1e-9)
    }
}
