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
 * `maxTextSize * lineHeight`: 13% of 108 is a 14.04 cap height, so a band 21.06 thick centred on the
 * line at 77.76. Thickness is perpendicular to the ribbon and the quad is built from axis
 * intercepts, so those edges land 29.78 apart along each axis — 62.87 and 92.65.
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

    private val sqrt2 = Math.sqrt(2.0)

    @Test
    fun `the centre line is a fixed fraction of the edge, whatever the band does`() {
        // The property the whole redesign rests on: band thickness must not move the ribbon. If it
        // does, sizing the band from the text feeds back into how much room the text has.
        val thin = ribbon(BannerCorner.TOP_LEFT, maxTextSizePercent = 4.0)
        val thick = ribbon(BannerCorner.TOP_LEFT, maxTextSizePercent = 20.0)
        assertEquals(0.72 * 108, thin.centreLineAxis, 1e-9)
        assertEquals(0.72 * 108, thick.centreLineAxis, 1e-9)
        assertEquals(thin.pivotX, thick.pivotX, 1e-9)
        assertEquals(thin.pivotY, thick.pivotY, 1e-9)
        assertEquals(thin.textLengthBudget, thick.textLengthBudget, 1e-9)
    }

    @Test
    fun `the band is centred on that line and grows both ways`() {
        val ribbon = ribbon(BannerCorner.TOP_LEFT)
        assertEquals(21.06, ribbon.bandThickness, 1e-9)
        // Along the axes the two edges are √2 further apart than the band is thick, because the band
        // runs at 45°. They stay symmetric about the centre line either way.
        assertEquals(21.06 * sqrt2, ribbon.bandWidthAxis, 1e-9)
        assertEquals(77.76 + 21.06 * sqrt2 / 2, ribbon.innerEdgeAxis, 1e-9)
        assertEquals(77.76 - 21.06 * sqrt2 / 2, ribbon.cornerSideEdgeAxis, 1e-9)
    }

    /**
     * The band's two long edges, projected onto the band's own normal, which is the only measurement
     * a reader of the icon can see.
     *
     * This is the assertion whose absence let a real bug ship: every other test here checks
     * coordinates, and the coordinates were self-consistent while being √2 too close together.
     */
    private fun perpendicularBandWidth(corner: BannerCorner, ribbon: Ribbon): Double {
        // Unit normal to a 45° band: the two diagonals, depending on which way the ribbon runs.
        val (nx, ny) = when (corner) {
            BannerCorner.TOP_LEFT, BannerCorner.BOTTOM_RIGHT -> 1.0 / sqrt2 to 1.0 / sqrt2
            BannerCorner.TOP_RIGHT, BannerCorner.BOTTOM_LEFT -> 1.0 / sqrt2 to -1.0 / sqrt2
        }
        val projections = listOf(
            ribbon.p1x to ribbon.p1y,
            ribbon.p2x to ribbon.p2y,
            ribbon.p3x to ribbon.p3y,
            ribbon.p4x to ribbon.p4y,
        ).map { (x, y) -> x * nx + y * ny }
        // Two points per edge, so four corners must collapse onto exactly two positions. Otherwise
        // the quad is not a band at all and the separation below would be measuring nothing.
        assertEquals(
            2,
            projections.map { Math.round(it * 1e6) }.toSet().size,
            "$corner quad does not have two parallel long edges: $projections",
        )
        return projections.max() - projections.min()
    }

    @Test
    fun `the band is as thick as the line height asked for, measured across the ribbon`() {
        // `lineHeight` names a perpendicular thickness. Applying it along the axes instead made every
        // band 1/√2 of that — the glyphs ended up with 3% clearance at the default rather than 25%.
        BannerCorner.entries.forEach { corner ->
            listOf(1.0, 1.5, 2.5).forEach { lineHeight ->
                val ribbon = ribbon(corner, lineHeight = lineHeight)
                assertEquals(
                    ribbon.textSize * lineHeight,
                    perpendicularBandWidth(corner, ribbon),
                    1e-9,
                    "$corner at lineHeight $lineHeight",
                )
            }
            // And on a viewport that is not square, where the corner tables do their own arithmetic.
            val tall = ribbon(corner, width = 108.0, height = 200.0)
            assertEquals(tall.textSize * 1.5, perpendicularBandWidth(corner, tall), 1e-9, "$corner, tall viewport")
            val wide = ribbon(corner, width = 200.0, height = 108.0)
            assertEquals(wide.textSize * 1.5, perpendicularBandWidth(corner, wide), 1e-9, "$corner, wide viewport")
        }
    }

    @Test
    fun `the text fills the band as tightly as the line height says`() {
        // What the thickness is for, stated in the terms a user judges it by: at 1.5 the glyphs take
        // two thirds of the band and leave a quarter of their own height clear at each edge.
        val ribbon = ribbon(BannerCorner.TOP_LEFT)
        val band = perpendicularBandWidth(BannerCorner.TOP_LEFT, ribbon)
        assertEquals(1 / 1.5, ribbon.textSize / band, 1e-9)
        assertEquals(0.25, ribbon.padding / ribbon.textSize, 1e-9)
    }

    @Test
    fun `band thickness is the text size times the line height`() {
        assertEquals(14.04, ribbon(BannerCorner.TOP_LEFT).textSize, 1e-9)
        assertEquals(14.04 * 1.5, ribbon(BannerCorner.TOP_LEFT).bandThickness, 1e-9)
        assertEquals(14.04 * 2.5, ribbon(BannerCorner.TOP_LEFT, lineHeight = 2.5).bandThickness, 1e-9)
        // Normalised against the shorter edge, so the same setting looks the same on a 24 icon.
        val small = ribbon(BannerCorner.TOP_LEFT, width = 24.0, height = 24.0)
        assertEquals(24.0 * 0.13, small.textSize, 1e-9)
        assertEquals(24.0 * 0.13 * 1.5, small.bandThickness, 1e-9)
    }

    @Test
    fun `line height changes the band without changing the text`() {
        val tight = ribbon(BannerCorner.TOP_LEFT, lineHeight = 1.0, textWidthPerCapHeight = 6.0)
        val loose = ribbon(BannerCorner.TOP_LEFT, lineHeight = 2.0, textWidthPerCapHeight = 6.0)
        assertEquals(tight.textSize, loose.textSize, 1e-9)
        assertEquals(2 * tight.bandThickness, loose.bandThickness, 1e-9)
        // Nor the text's position: the centre line is pinned and the band grows symmetrically.
        assertEquals(tight.pivotX, loose.pivotX, 1e-9)
        assertEquals(tight.pivotY, loose.pivotY, 1e-9)
        assertEquals(tight.textRotationDegrees, loose.textRotationDegrees)
        // Padding is not a knob of its own: it is whatever the line height left over.
        assertEquals(0.0, tight.padding, 1e-9)
        assertEquals(tight.textSize / 2, loose.padding, 1e-9)
    }

    @Test
    fun `long text shrinks below maxTextSize and takes the band with it`() {
        val short = ribbon(BannerCorner.TOP_LEFT, textWidthPerCapHeight = 1.5)
        assertEquals(14.04, short.textSize, 1e-9)
        assertEquals(14.04 * 1.5, short.bandThickness, 1e-9)

        // Seven characters of a monospaced face, near enough: the length budget binds instead.
        val long = ribbon(BannerCorner.TOP_LEFT, textWidthPerCapHeight = 5.6)
        assertTrue(long.textSize < 14.04, "long text should not reach maxTextSize")
        assertEquals(long.textLengthBudget / (5.6 + 2 * 0.3), long.textSize, 1e-9)
        assertEquals(long.textSize * 1.5, long.bandThickness, 1e-9)
        // The text plus the clearance at each end is exactly the budget, by construction. Both are
        // measured along the ribbon, the direction the text advances in, so no √2 belongs here.
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
        assertEquals(square.innerEdgeAxis, tall.innerEdgeAxis, 1e-9)
        assertEquals(square.bandThickness, tall.bandThickness, 1e-9)
        assertEquals(square.textLengthBudget, tall.textLengthBudget, 1e-9)
    }

    @Test
    fun `top left quad runs from the x axis to the y axis`() {
        assertEquals(
            "M 92.65 0 L 0 92.65 L 0 62.87 L 62.87 0 Z",
            ribbon(BannerCorner.TOP_LEFT).quadPathData(),
        )
    }

    @Test
    fun `top right quad is mirrored into the right edge`() {
        assertEquals(
            "M 15.35 0 L 108 92.65 L 108 62.87 L 45.13 0 Z",
            ribbon(BannerCorner.TOP_RIGHT).quadPathData(),
        )
    }

    @Test
    fun `bottom left quad is mirrored into the bottom edge`() {
        assertEquals(
            "M 0 15.35 L 92.65 108 L 62.87 108 L 0 45.13 Z",
            ribbon(BannerCorner.BOTTOM_LEFT).quadPathData(),
        )
    }

    @Test
    fun `bottom right quad is mirrored into both edges`() {
        assertEquals(
            "M 15.35 108 L 108 15.35 L 108 45.13 L 45.13 108 Z",
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
        // the mask, which is what sheared the first and last glyphs off on a real device. Both the
        // radius and the offset are true distances, so the chord is one too — it is compared against
        // the text's own advance, never against an axis measurement.
        val offset = (108.0 - 77.76) / sqrt2
        assertEquals(offset, ribbon.perpendicularFromIconCentre(ribbon.centreLineAxis), 1e-9)
        val acrossSafeZone = 2 * Math.sqrt(33.0 * 33.0 - offset * offset)
        assertEquals(acrossSafeZone, ribbon.textLengthBudget, 1e-9)
        assertTrue(
            acrossSafeZone < 77.76 * sqrt2,
            "the safe zone should be far tighter than the chord across the square",
        )
    }

    @Test
    fun `the whole band stays inside the safe zone at the default style`() {
        // Not a constraint — a thicker band is allowed to run past the rim, and the mask just does
        // not draw that part — but it is the trade CENTRE_LINE_FRACTION and the default line height
        // were picked to satisfy: at the default nothing of the band is lost to any mask, and the
        // band still does not run through the middle of the artwork.
        val ribbon = ribbon(BannerCorner.TOP_LEFT)
        val safeRadius = Ribbon.SAFE_ZONE_FRACTION * 108.0
        val cornerSideOffset = ribbon.perpendicularFromIconCentre(ribbon.cornerSideEdgeAxis)
        assertTrue(cornerSideOffset < safeRadius, "corner-side edge at $cornerSideOffset is outside the safe zone")
        val innerOffset = ribbon.perpendicularFromIconCentre(ribbon.innerEdgeAxis)
        assertTrue(innerOffset > 0.08 * 108.0, "inner edge at $innerOffset all but covers the icon's centre")
    }

    @Test
    fun `inverse clip is the corner triangle plus the rest of the icon`() {
        assertEquals(
            "M 0 0 L 62.87 0 L 0 62.87 Z M 92.65 0 L 108 0 L 108 108 L 0 108 L 0 92.65 Z",
            ribbon(BannerCorner.TOP_LEFT).inverseClipPathData(),
        )
        assertEquals(
            "M 108 0 L 45.13 0 L 108 62.87 Z M 15.35 0 L 0 0 L 0 108 L 108 108 L 108 92.65 Z",
            ribbon(BannerCorner.TOP_RIGHT).inverseClipPathData(),
        )
        assertEquals(
            "M 0 108 L 62.87 108 L 0 45.13 Z M 0 15.35 L 0 0 L 108 0 L 108 108 L 92.65 108 Z",
            ribbon(BannerCorner.BOTTOM_LEFT).inverseClipPathData(),
        )
        assertEquals(
            "M 108 108 L 45.13 108 L 108 45.13 Z M 15.35 108 L 0 108 L 0 0 L 108 0 L 108 15.35 Z",
            ribbon(BannerCorner.BOTTOM_RIGHT).inverseClipPathData(),
        )
    }

    @Test
    fun `the inverse clip meets the band exactly, whatever the thickness`() {
        // The clip is the complement of the drawn quad, so its inner boundary has to sit on the
        // band's own two edges — to the last decimal, or the monochrome layer shows a hairline of
        // artwork bleeding into the band, or a sliver of band missing.
        BannerCorner.entries.forEach { corner ->
            listOf(1.0, 1.5, 3.0).forEach { lineHeight ->
                val ribbon = ribbon(corner, lineHeight = lineHeight)
                val quad = coordinates(ribbon.quadPathData())
                val clip = coordinates(ribbon.inverseClipPathData())
                assertTrue(
                    quad.all { it in clip },
                    "$corner at lineHeight $lineHeight: clip $clip does not carry the band's edges $quad",
                )
            }
        }
    }

    /** Every coordinate pair in a `pathData` string, as text so the comparison is exact. */
    private fun coordinates(pathData: String): Set<String> =
        Regex("-?[\\d.]+ -?[\\d.]+").findAll(pathData).map { it.value }.toSet()

    @Test
    fun `no corner produces a degenerate clip triangle`() {
        // The corner triangle is built from one point on each of the two edges meeting at the
        // corner. Reading those from the wrong quad points collapses it to a single point, which
        // clips nothing at all and would only show up as monochrome artwork bleeding into the band.
        BannerCorner.entries.forEach { corner ->
            val triangle = ribbon(corner).inverseClipPathData().substringBefore(" M ")
            assertTrue(coordinates(triangle).size == 3, "Corner triangle for $corner is degenerate: $triangle")
        }
    }
}
