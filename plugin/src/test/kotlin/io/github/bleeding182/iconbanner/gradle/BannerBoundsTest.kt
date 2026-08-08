package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.api.BannerCorner
import io.github.bleeding182.iconbanner.generator.Ribbon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BannerBoundsTest {

    private val defaultLineHeight = BannerDefaults.LINE_HEIGHT
    private val defaultPosition = BannerDefaults.POSITION.toDouble()

    private fun check(
        maxTextSize: Int,
        lineHeight: Double = defaultLineHeight,
        position: Int = BannerDefaults.POSITION,
        monochromeAlpha: Int = BannerDefaults.MONOCHROME_ALPHA,
    ) = BannerBounds.check(maxTextSize, lineHeight, position, monochromeAlpha, "devDebug", "main")

    /** One knob: how to set it, what the message calls it, and the range that message must quote. */
    private class Knob(
        val property: String,
        val range: String,
        val bad: List<Number>,
        val good: List<Number>,
        val set: (Number) -> Unit,
    )

    private val knobs = listOf(
        Knob(
            "maxTextSize", "${BannerBounds.MIN_TEXT_SIZE}..${BannerBounds.MAX_TEXT_SIZE}",
            bad = listOf(0, -1, -20, BannerBounds.MAX_TEXT_SIZE + 1, 100, 1000),
            good = listOf(BannerBounds.MIN_TEXT_SIZE, 8, BannerDefaults.MAX_TEXT_SIZE, BannerBounds.MAX_TEXT_SIZE),
        ) { check(it.toInt()) },
        Knob(
            "lineHeight", "1..3",
            bad = listOf(0.0, -1.0, 0.99, 3.01, 40.0),
            good = listOf(BannerBounds.MIN_LINE_HEIGHT, 2.4, BannerBounds.MAX_LINE_HEIGHT),
        ) { check(13, lineHeight = it.toDouble()) },
        Knob(
            "position", "${BannerBounds.MIN_POSITION}..${BannerBounds.MAX_POSITION}",
            bad = listOf(0, -1, 19, 96, 100, 1000),
            good = listOf(BannerBounds.MIN_POSITION, 46, BannerDefaults.POSITION, 90, BannerBounds.MAX_POSITION),
        ) { check(13, position = it.toInt()) },
        Knob(
            "monochromeAlpha",
            "${BannerBounds.MIN_MONOCHROME_ALPHA}..${BannerBounds.MAX_MONOCHROME_ALPHA}",
            bad = listOf(-1, 101, 255, 1000),
            good = listOf(BannerBounds.MIN_MONOCHROME_ALPHA, 80, BannerBounds.MAX_MONOCHROME_ALPHA),
        ) { check(13, monochromeAlpha = it.toInt()) },
    )

    @Test
    fun `every knob rejects what is outside its range and accepts the whole of it`() {
        for (knob in knobs) {
            for (value in knob.bad) {
                val failure = assertThrows(IllegalArgumentException::class.java) { knob.set(value) }
                val message = failure.message!!
                assertTrue("iconBanner.${knob.property}" in message, message)
                assertTrue(knob.range in message, message)
                assertTrue("devDebug" in message, message)
            }
            for (value in knob.good) {
                knob.set(value)
            }
        }
    }

    @Test
    fun `the message quotes the offending value and names the banner, not just the variant`() {
        // A variant merges each banner from different blocks, so the variant alone is not enough.
        val failure = assertThrows(IllegalArgumentException::class.java) {
            BannerBounds.check(
                99, defaultLineHeight, BannerDefaults.POSITION, BannerDefaults.MONOCHROME_ALPHA,
                "devDebug", "sha",
            )
        }
        assertTrue(failure.message!!.contains("99"), failure.message)
        assertTrue(failure.message!!.contains("banner 'sha'"), failure.message)
        assertTrue(failure.message!!.contains("variant 'devDebug'"), failure.message)
    }

    @Test
    fun `the text size range does not move with the line height`() {
        // Thickness costs the text nothing, so it cannot narrow the range of sizes.
        for (lineHeight in listOf(1.0, 1.5, 2.4, BannerBounds.MAX_LINE_HEIGHT)) {
            check(BannerDefaults.MAX_TEXT_SIZE, lineHeight)
            check(BannerBounds.MIN_TEXT_SIZE, lineHeight)
            check(BannerBounds.MAX_TEXT_SIZE, lineHeight)
        }
        // And the message says nothing about the line height, because the bound does not depend on it.
        val failure = assertThrows(IllegalArgumentException::class.java) {
            check(BannerBounds.MAX_TEXT_SIZE + 1, lineHeight = 2.4)
        }
        assertTrue(!failure.message!!.contains("lineHeight"), failure.message)
    }

    @Test
    fun `the text size bound is the last one that keeps the glyphs inside the safe zone`() {
        // Measured from the *requested* percentage, not ribbon.textSize: Ribbon clamps the fitted size,
        // so reading it back could never show the bound too low.
        fun glyphEdgeFromCentre(textPercent: Double): Double {
            val ribbon = Ribbon(108.0, 108.0, BannerCorner.TOP_LEFT, defaultPosition, textPercent, 1.0)
            return ribbon.centreLineFromCentre + textPercent / 100.0 * 108.0 / 2.0
        }

        val safeRadius = Ribbon.SAFE_ZONE_FRACTION * 108.0
        assertTrue(
            glyphEdgeFromCentre(BannerBounds.MAX_TEXT_SIZE.toDouble()) <= safeRadius,
            "text at ${BannerBounds.MAX_TEXT_SIZE}% already leaves the safe zone",
        )
        assertTrue(
            glyphEdgeFromCentre(BannerBounds.MAX_TEXT_SIZE + 1.0) > safeRadius,
            "text one point larger is still inside the safe zone, so the bound is too low",
        )
    }

    @Test
    fun `the position range stops short of the point where no text fits`() {
        // The chord reaches zero at 100, so the range has to end before it.
        val atMax = Ribbon(
            108.0, 108.0, BannerCorner.TOP_LEFT,
            BannerBounds.MAX_POSITION.toDouble(), 13.0, 1.0,
        )
        assertTrue(atMax.textLengthBudget > 0.0, "no room for text at all at MAX_POSITION")
        val atHundred = Ribbon(108.0, 108.0, BannerCorner.TOP_LEFT, 100.0, 13.0, 1.0)
        assertEquals(0.0, atHundred.textLengthBudget, 1e-9)
    }

    @Test
    fun `the default position is the one the geometry documents`() {
        // Two layers hold this number, and the Gradle layer may not read the generator's at runtime.
        assertEquals(BannerDefaults.POSITION.toDouble(), Ribbon.DEFAULT_POSITION_PERCENT, 1e-9)
    }

    @Test
    fun `the length the text is fitted against is the same at every bound`() {
        // Neither knob may eat the length the text needs, at any value in range.
        fun ribbon(textPercent: Int, lineHeight: Double) =
            Ribbon(108.0, 108.0, BannerCorner.TOP_LEFT, defaultPosition, textPercent.toDouble(), lineHeight)

        val thin = ribbon(BannerBounds.MIN_TEXT_SIZE, BannerBounds.MIN_LINE_HEIGHT)
        val widest = ribbon(BannerBounds.MAX_TEXT_SIZE, defaultLineHeight)
        val thickest = ribbon(BannerDefaults.MAX_TEXT_SIZE, BannerBounds.MAX_LINE_HEIGHT)
        assertTrue(thin.textLengthBudget > 0.0, "no room for text at all")
        assertEquals(thin.textLengthBudget, widest.textLengthBudget, 1e-9)
        assertEquals(thin.textLengthBudget, thickest.textLengthBudget, 1e-9)
    }
}
