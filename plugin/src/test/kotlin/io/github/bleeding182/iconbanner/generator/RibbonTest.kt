package io.github.bleeding182.iconbanner.generator

import io.github.bleeding182.iconbanner.api.BannerCorner
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Geometry in isolation. The golden files cover how the ribbon looks in a real vector; these cover
 * the arithmetic, where an off-by-one in a corner table is invisible in a diff of glyph outlines.
 *
 * The default fixture leaves the text out, which is the case where the band reaches its full
 * `maxTextSize * lineHeight`: 13% of 108 is a 14.04 cap height, so a 21.06 band centred on 77.76,
 * running from 67.23 to 88.29.
 */
class RibbonTest {

    private fun ribbon(
        corner: BannerCorner,
        width: Double = 108.0,
        height: Double = 108.0,
        maxTextSizePercent: Double = 13.0,
        lineHeight: Double = 1.5,
        textWidthPerCapHeight: Double? = null,
    ) = Ribbon(width, height, corner, maxTextSizePercent, lineHeight, textWidthPerCapHeight)

    @Test
    fun `the centre line is a fixed fraction of the edge, whatever the band does`() {
        // The property the whole redesign rests on: band width must not move the ribbon. If it does,
        // sizing the band from the text feeds back into how much room the text has.
        val thin = ribbon(BannerCorner.TOP_LEFT, maxTextSizePercent = 4.0)
        val thick = ribbon(BannerCorner.TOP_LEFT, maxTextSizePercent = 20.0)
        assertEquals(0.72 * 108, thin.centreLine, 1e-9)
        assertEquals(0.72 * 108, thick.centreLine, 1e-9)
        assertEquals(thin.pivotX, thick.pivotX, 1e-9)
        assertEquals(thin.pivotY, thick.pivotY, 1e-9)
        assertEquals(thin.textLengthBudget, thick.textLengthBudget, 1e-9)
    }

    @Test
    fun `the band is centred on that line and grows both ways`() {
        val ribbon = ribbon(BannerCorner.TOP_LEFT)
        assertEquals(21.06, ribbon.bandWidth, 1e-9)
        assertEquals(77.76 + 10.53, ribbon.reach, 1e-9)
        assertEquals(77.76 - 10.53, ribbon.cornerSideEdge, 1e-9)
    }

    @Test
    fun `band width is the text size times the line height`() {
        assertEquals(14.04, ribbon(BannerCorner.TOP_LEFT).textSize, 1e-9)
        assertEquals(14.04 * 1.5, ribbon(BannerCorner.TOP_LEFT).bandWidth, 1e-9)
        assertEquals(14.04 * 2.5, ribbon(BannerCorner.TOP_LEFT, lineHeight = 2.5).bandWidth, 1e-9)
        // Normalised against the shorter edge, so the same setting looks the same on a 24 icon.
        val small = ribbon(BannerCorner.TOP_LEFT, width = 24.0, height = 24.0)
        assertEquals(24.0 * 0.13, small.textSize, 1e-9)
        assertEquals(24.0 * 0.13 * 1.5, small.bandWidth, 1e-9)
    }

    @Test
    fun `line height changes the band without changing the text`() {
        val tight = ribbon(BannerCorner.TOP_LEFT, lineHeight = 1.0, textWidthPerCapHeight = 6.0)
        val loose = ribbon(BannerCorner.TOP_LEFT, lineHeight = 2.0, textWidthPerCapHeight = 6.0)
        assertEquals(tight.textSize, loose.textSize, 1e-9)
        assertEquals(2 * tight.bandWidth, loose.bandWidth, 1e-9)
        // Padding is not a knob of its own: it is whatever the line height left over.
        assertEquals(0.0, tight.padding, 1e-9)
        assertEquals(tight.textSize / 2, loose.padding, 1e-9)
    }

    @Test
    fun `long text shrinks below maxTextSize and takes the band with it`() {
        val short = ribbon(BannerCorner.TOP_LEFT, textWidthPerCapHeight = 1.5)
        assertEquals(14.04, short.textSize, 1e-9)
        assertEquals(14.04 * 1.5, short.bandWidth, 1e-9)

        // Seven characters of a monospaced face, near enough: the length budget binds instead.
        val long = ribbon(BannerCorner.TOP_LEFT, textWidthPerCapHeight = 5.6)
        assertTrue(long.textSize < 14.04, "long text should not reach maxTextSize")
        assertEquals(long.textLengthBudget / (5.6 + 2 * 0.3), long.textSize, 1e-9)
        assertEquals(long.textSize * 1.5, long.bandWidth, 1e-9)
        // The text plus the clearance at each end is exactly the budget, by construction.
        assertEquals(
            long.textLengthBudget,
            long.textSize * 5.6 + 2 * long.endPadding,
            1e-9,
        )
    }

    @Test
    fun `a non-square viewport normalises against the shorter edge`() {
        // Same band on a tall icon as on a square one of the same width, so the setting stays
        // portable between projects with differently shaped foregrounds.
        val tall = ribbon(BannerCorner.TOP_LEFT, width = 108.0, height = 200.0)
        val square = ribbon(BannerCorner.TOP_LEFT)
        assertEquals(square.reach, tall.reach, 1e-9)
        assertEquals(square.bandWidth, tall.bandWidth, 1e-9)
        assertEquals(square.textLengthBudget, tall.textLengthBudget, 1e-9)
    }

    @Test
    fun `top left quad runs from the x axis to the y axis`() {
        assertEquals(
            "M 88.29 0 L 0 88.29 L 0 67.23 L 67.23 0 Z",
            ribbon(BannerCorner.TOP_LEFT).quadPathData(),
        )
    }

    @Test
    fun `top right quad is mirrored into the right edge`() {
        assertEquals(
            "M 19.71 0 L 108 88.29 L 108 67.23 L 40.77 0 Z",
            ribbon(BannerCorner.TOP_RIGHT).quadPathData(),
        )
    }

    @Test
    fun `bottom left quad is mirrored into the bottom edge`() {
        assertEquals(
            "M 0 19.71 L 88.29 108 L 67.23 108 L 0 40.77 Z",
            ribbon(BannerCorner.BOTTOM_LEFT).quadPathData(),
        )
    }

    @Test
    fun `bottom right quad is mirrored into both edges`() {
        assertEquals(
            "M 19.71 108 L 108 19.71 L 108 40.77 L 40.77 108 Z",
            ribbon(BannerCorner.BOTTOM_RIGHT).quadPathData(),
        )
    }

    @Test
    fun `text reads left to right in every corner`() {
        // Top-left and bottom-right run down-right; the other two run up-right. Getting a sign
        // wrong here renders the text upside down, which no golden file would obviously flag.
        assertEquals(-45.0, ribbon(BannerCorner.TOP_LEFT).textRotationDegrees)
        assertEquals(45.0, ribbon(BannerCorner.TOP_RIGHT).textRotationDegrees)
        assertEquals(45.0, ribbon(BannerCorner.BOTTOM_LEFT).textRotationDegrees)
        assertEquals(-45.0, ribbon(BannerCorner.BOTTOM_RIGHT).textRotationDegrees)
    }

    @Test
    fun `pivot is on the centre line`() {
        val topLeft = ribbon(BannerCorner.TOP_LEFT)
        assertEquals(77.76 / 2, topLeft.pivotX, 1e-9)
        assertEquals(77.76 / 2, topLeft.pivotY, 1e-9)

        val bottomRight = ribbon(BannerCorner.BOTTOM_RIGHT)
        assertEquals(108.0 - 77.76 / 2, bottomRight.pivotX, 1e-9)
        assertEquals(108.0 - 77.76 / 2, bottomRight.pivotY, 1e-9)
    }

    @Test
    fun `the text length budget is the safe-zone chord`() {
        val ribbon = ribbon(BannerCorner.TOP_LEFT)
        // The chord across the safe zone, not across the square: the square would let text run past
        // the mask, which is what sheared the first and last glyphs off on a real device.
        val offset = (108.0 - 77.76) / Math.sqrt(2.0)
        val acrossSafeZone = 2 * Math.sqrt(33.0 * 33.0 - offset * offset)
        assertEquals(acrossSafeZone, ribbon.textLengthBudget, 1e-9)
        assertTrue(
            acrossSafeZone < 77.76 * Math.sqrt(2.0),
            "the safe zone should be far tighter than the chord across the square",
        )
    }

    @Test
    fun `the whole band stays inside the safe zone at the default style`() {
        // The criterion CENTRE_LINE_FRACTION was chosen against: a launcher masks the icon, and an
        // edge outside the safe zone means the band renders thinner than it was asked to be.
        val ribbon = ribbon(BannerCorner.TOP_LEFT)
        val safeRadius = Ribbon.SAFE_ZONE_FRACTION * 108.0
        val cornerSideOffset = (108.0 - ribbon.cornerSideEdge) / Math.sqrt(2.0)
        assertTrue(cornerSideOffset < safeRadius, "corner-side edge at $cornerSideOffset is outside the safe zone")
        // And the other half of the trade: it does not run through the middle of the icon either.
        val innerOffset = (108.0 - ribbon.reach) / Math.sqrt(2.0)
        assertTrue(innerOffset > 0.10 * 108.0, "inner edge at $innerOffset all but covers the icon's centre")
    }

    @Test
    fun `inverse clip is the corner triangle plus the rest of the icon`() {
        assertEquals(
            "M 0 0 L 67.23 0 L 0 67.23 Z M 88.29 0 L 108 0 L 108 108 L 0 108 L 0 88.29 Z",
            ribbon(BannerCorner.TOP_LEFT).inverseClipPathData(),
        )
        assertEquals(
            "M 108 0 L 40.77 0 L 108 67.23 Z M 19.71 0 L 0 0 L 0 108 L 108 108 L 108 88.29 Z",
            ribbon(BannerCorner.TOP_RIGHT).inverseClipPathData(),
        )
        assertEquals(
            "M 0 108 L 67.23 108 L 0 40.77 Z M 0 19.71 L 0 0 L 108 0 L 108 108 L 88.29 108 Z",
            ribbon(BannerCorner.BOTTOM_LEFT).inverseClipPathData(),
        )
        assertEquals(
            "M 108 108 L 40.77 108 L 108 40.77 Z M 19.71 108 L 0 108 L 0 0 L 108 0 L 108 19.71 Z",
            ribbon(BannerCorner.BOTTOM_RIGHT).inverseClipPathData(),
        )
    }

    @Test
    fun `no corner produces a degenerate clip triangle`() {
        // The corner triangle is built from one point on each of the two edges meeting at the
        // corner. Reading those from the wrong quad points collapses it to a single point, which
        // clips nothing at all and would only show up as monochrome artwork bleeding into the band.
        BannerCorner.entries.forEach { corner ->
            val triangle = ribbon(corner).inverseClipPathData().substringBefore(" M ")
            val points = Regex("-?[\\d.]+ -?[\\d.]+").findAll(triangle).map { it.value }.toSet()
            assertTrue(points.size == 3, "Corner triangle for $corner is degenerate: $triangle")
        }
    }
}
