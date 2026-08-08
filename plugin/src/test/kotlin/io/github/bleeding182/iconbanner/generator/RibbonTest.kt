package io.github.bleeding182.iconbanner.generator

import io.github.bleeding182.iconbanner.api.BannerCorner
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Geometry in isolation. The golden files cover how the ribbon looks in a real vector; these cover
 * the arithmetic, where an off-by-one in a corner table is invisible in a diff of glyph outlines.
 *
 * The default fixture has no text, so the band reaches its full `maxTextSize * lineHeight`: a 14.04
 * cap height and a 21.06 band on the line at 77.67, whose edges land 29.78 apart along each axis at
 * 62.77 and 92.56.
 */
class RibbonTest {

    private fun ribbon(
        corner: BannerCorner,
        width: Double = 108.0,
        height: Double = 108.0,
        positionPercent: Double = Ribbon.DEFAULT_POSITION_PERCENT,
        maxTextSizePercent: Double = 13.0,
        lineHeight: Double = 1.5,
        textWidthPerCapHeight: Double? = null,
    ) = Ribbon(width, height, corner, positionPercent, maxTextSizePercent, lineHeight, textWidthPerCapHeight)

    private val sqrt2 = Math.sqrt(2.0)

    private fun round(value: Double): Double = Math.round(value * 100.0) / 100.0

    /** The default centre line, derived rather than written as 77.67, which would assert nothing. */
    private val centreLine = 108.0 - Ribbon.DEFAULT_POSITION_PERCENT / 100.0 * 33.0 * sqrt2

    @Test
    fun `the centre line is where position put it, whatever the band does`() {
        // Or sizing the band from the text feeds back into how much room the text has.
        val thin = ribbon(BannerCorner.TOP_LEFT, maxTextSizePercent = 4.0)
        val thick = ribbon(BannerCorner.TOP_LEFT, maxTextSizePercent = 20.0)
        assertEquals(centreLine, thin.centreLineAxis, 1e-9)
        assertEquals(centreLine, thick.centreLineAxis, 1e-9)
        assertEquals(thin.pivotX, thick.pivotX, 1e-9)
        assertEquals(thin.pivotY, thick.pivotY, 1e-9)
        assertEquals(thin.textLengthBudget, thick.textLengthBudget, 1e-9)
    }

    @Test
    fun `the band is centred on that line and grows both ways`() {
        val ribbon = ribbon(BannerCorner.TOP_LEFT)
        assertEquals(21.06, ribbon.bandThickness, 1e-9)
        // √2 further apart along the axes, because the band runs at 45°.
        assertEquals(21.06 * sqrt2, ribbon.bandWidthAxis, 1e-9)
        assertEquals(centreLine + 21.06 * sqrt2 / 2, ribbon.innerEdgeAxis, 1e-9)
        assertEquals(centreLine - 21.06 * sqrt2 / 2, ribbon.cornerSideEdgeAxis, 1e-9)
    }

    /**
     * The band's two long edges projected onto its own normal — the only measurement a reader of the
     * icon can see, and the one every coordinate assertion here misses.
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
        // Four corners must collapse onto two positions, or it is not a band at all.
        assertEquals(
            2,
            projections.map { Math.round(it * 1e6) }.toSet().size,
            "$corner quad does not have two parallel long edges: $projections",
        )
        return projections.max() - projections.min()
    }

    @Test
    fun `the band is as thick as the line height asked for, measured across the ribbon`() {
        // lineHeight names a perpendicular thickness. Applied along the axes it drew 1/√2 of that.
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
        // At 1.5 the glyphs take two thirds of the band.
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
        // Both measured along the ribbon, so no √2 belongs here.
        assertEquals(
            long.textLengthBudget,
            long.textSize * 5.6 + 2 * long.endPadding,
            1e-9,
        )
    }

    @Test
    fun `a non-square viewport normalises against the shorter edge`() {
        // Same band on a tall icon as on a square one, so settings stay portable.
        val tall = ribbon(BannerCorner.TOP_LEFT, width = 108.0, height = 200.0)
        val square = ribbon(BannerCorner.TOP_LEFT)
        assertEquals(square.innerEdgeAxis, tall.innerEdgeAxis, 1e-9)
        assertEquals(square.bandThickness, tall.bandThickness, 1e-9)
        assertEquals(square.textLengthBudget, tall.textLengthBudget, 1e-9)
    }

    @Test
    fun `each corner's quad is the top left one mirrored into that corner`() {
        // One literal pins the axis convention; the other three are derived, because a corner table
        // is exactly the kind of thing where hand-recomputed expectations copy the bug.
        val topLeft = "M 92.56 0 L 0 92.56 L 0 62.77 L 62.77 0 Z"
        assertEquals(topLeft, ribbon(BannerCorner.TOP_LEFT).quadPathData())

        // Sets, not lists: mirroring a quad reverses the order its points come out in. Rounded to the
        // two decimals pathData carries, or 108 - 92.56 misses by a float's worth.
        fun mirror(flipX: Boolean, flipY: Boolean) = pathPoints(topLeft)
            .map { (x, y) -> round(if (flipX) 108 - x else x) to round(if (flipY) 108 - y else y) }
            .toSet()

        fun quadPoints(corner: BannerCorner) = pathPoints(ribbon(corner).quadPathData()).toSet()
        assertEquals(mirror(flipX = true, flipY = false), quadPoints(BannerCorner.TOP_RIGHT))
        assertEquals(mirror(flipX = false, flipY = true), quadPoints(BannerCorner.BOTTOM_LEFT))
        assertEquals(mirror(flipX = true, flipY = true), quadPoints(BannerCorner.BOTTOM_RIGHT))
    }

    @Test
    fun `text reads left to right in every corner`() {
        // A wrong sign renders the text upside down, which no golden file would flag.
        assertEquals(-45.0, ribbon(BannerCorner.TOP_LEFT).textRotationDegrees)
        assertEquals(45.0, ribbon(BannerCorner.TOP_RIGHT).textRotationDegrees)
        assertEquals(45.0, ribbon(BannerCorner.BOTTOM_LEFT).textRotationDegrees)
        assertEquals(-45.0, ribbon(BannerCorner.BOTTOM_RIGHT).textRotationDegrees)
    }

    @Test
    fun `pivot is on the centre line`() {
        val topLeft = ribbon(BannerCorner.TOP_LEFT)
        assertEquals(centreLine / 2, topLeft.pivotX, 1e-9)
        assertEquals(centreLine / 2, topLeft.pivotY, 1e-9)

        val bottomRight = ribbon(BannerCorner.BOTTOM_RIGHT)
        assertEquals(108.0 - centreLine / 2, bottomRight.pivotX, 1e-9)
        assertEquals(108.0 - centreLine / 2, bottomRight.pivotY, 1e-9)
    }

    @Test
    fun `the text length budget is the safe-zone chord`() {
        val ribbon = ribbon(BannerCorner.TOP_LEFT)
        // The safe zone, not the square: the square lets text run past the mask.
        val offset = (108.0 - centreLine) / sqrt2
        assertEquals(offset, ribbon.perpendicularFromIconCentre(ribbon.centreLineAxis), 1e-9)
        val acrossSafeZone = 2 * Math.sqrt(33.0 * 33.0 - offset * offset)
        assertEquals(acrossSafeZone, ribbon.textLengthBudget, 1e-9)
        assertTrue(
            acrossSafeZone < centreLine * sqrt2,
            "the safe zone should be far tighter than the chord across the square",
        )
    }

    @Test
    fun `the whole band stays inside the safe zone at the default style`() {
        // Not a constraint, but the trade the default position and line height were picked for.
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
            "M 0 0 L 62.77 0 L 0 62.77 Z M 92.56 0 L 108 0 L 108 108 L 0 108 L 0 92.56 Z",
            ribbon(BannerCorner.TOP_LEFT).inverseClipPathData(),
        )
        assertEquals(
            "M 108 0 L 45.23 0 L 108 62.77 Z M 15.44 0 L 0 0 L 0 108 L 108 108 L 108 92.56 Z",
            ribbon(BannerCorner.TOP_RIGHT).inverseClipPathData(),
        )
        assertEquals(
            "M 0 108 L 62.77 108 L 0 45.23 Z M 0 15.44 L 0 0 L 108 0 L 108 108 L 92.56 108 Z",
            ribbon(BannerCorner.BOTTOM_LEFT).inverseClipPathData(),
        )
        assertEquals(
            "M 108 108 L 45.23 108 L 108 45.23 Z M 15.44 108 L 0 108 L 0 0 L 108 0 L 108 15.44 Z",
            ribbon(BannerCorner.BOTTOM_RIGHT).inverseClipPathData(),
        )
    }

    @Test
    fun `the inverse clip meets the band exactly, whatever the thickness`() {
        // The clip's inner boundary must sit on the band's edges to the last decimal, or the
        // monochrome layer shows a hairline of artwork.
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
        // Reading the triangle off the wrong quad points collapses it to a point, which clips nothing.
        BannerCorner.entries.forEach { corner ->
            val triangle = ribbon(corner).inverseClipPathData().substringBefore(" M ")
            assertTrue(coordinates(triangle).size == 3, "Corner triangle for $corner is degenerate: $triangle")
        }
    }

    @Test
    fun `position is a fraction of the distance at which the text budget runs out`() {
        // Anchored on the safe radius, not the canvas, so position means the same at a corner or an edge.
        val safeRadius = Ribbon.SAFE_ZONE_FRACTION * 108.0
        listOf(20.0, 46.0, 65.0, 76.0, 95.0).forEach { position ->
            val ribbon = ribbon(BannerCorner.TOP_LEFT, positionPercent = position)
            assertEquals(position / 100.0 * safeRadius, ribbon.centreLineFromCentre, 1e-9, "at $position")
            // The closed form the docs quote, which is what makes the trade predictable.
            val fraction = position / 100.0
            assertEquals(
                2 * safeRadius * Math.sqrt(1 - fraction * fraction),
                ribbon.textLengthBudget,
                1e-9,
                "at $position",
            )
        }
        // And the endpoint is real rather than a convention.
        assertEquals(0.0, ribbon(BannerCorner.TOP_LEFT, positionPercent = 100.0).textLengthBudget, 1e-9)
    }

    @Test
    fun `the axis intercept and the distance from the centre agree`() {
        // The inverse of how centreLineAxis is derived, so a round trip must land where it started.
        listOf(20.0, 65.0, 95.0).forEach { position ->
            val ribbon = ribbon(BannerCorner.TOP_LEFT, positionPercent = position)
            assertEquals(
                ribbon.centreLineFromCentre,
                ribbon.perpendicularFromIconCentre(ribbon.centreLineAxis),
                1e-9,
                "at $position",
            )
        }
    }

    @Test
    fun `pushing the band out shrinks the ribbon and the text with it`() {
        // Both dimensions shrink: the length with the chord, the thickness with the text.
        val text = 4.07 // about five characters of the demo app's display face
        val default = ribbon(BannerCorner.TOP_LEFT, textWidthPerCapHeight = text)
        val pushedOut = ribbon(BannerCorner.TOP_LEFT, positionPercent = 90.0, textWidthPerCapHeight = text)
        assertTrue(pushedOut.textLengthBudget < default.textLengthBudget, "the chord should shorten")
        assertTrue(pushedOut.textSize < default.textSize, "the text should shrink with it")
        assertTrue(pushedOut.bandThickness < default.bandThickness, "the band should hug the smaller text")
        // Pulling in buys very little, which is why the docs call this fine-tuning.
        val pulledIn = ribbon(BannerCorner.TOP_LEFT, positionPercent = 33.0, textWidthPerCapHeight = text)
        assertTrue(pulledIn.textSize > default.textSize, "the text should get more room")
        assertTrue(pulledIn.textSize < default.textSize * 1.25, "the curve is flat near the centre")
    }

    @Test
    fun `the safe zone caps the text once the band is pushed out`() {
        // Without this term a two-character marker at 90 keeps its full cap height and leaves the
        // safe zone.
        val safeRadius = Ribbon.SAFE_ZONE_FRACTION * 108.0
        val short = ribbon(BannerCorner.TOP_LEFT, positionPercent = 90.0, textWidthPerCapHeight = 1.2)
        assertTrue(short.textSize < 14.04, "the safe zone should bind before maxTextSize")
        assertEquals(2 * (safeRadius - short.centreLineFromCentre), short.textSize, 1e-9)
        // Which is exactly the glyphs' corner-side half landing on the rim, and no further.
        val glyphEdge = short.centreLineFromCentre + short.textSize / 2
        assertEquals(safeRadius, glyphEdge, 1e-9)
    }

    @Test
    fun `the safe zone cannot bind at the default position`() {
        // At 65 the safe zone allows 21.4%, so nothing a build script may ask for is clamped.
        val safeRadius = Ribbon.SAFE_ZONE_FRACTION * 108.0
        val ribbon = ribbon(BannerCorner.TOP_LEFT, maxTextSizePercent = 21.0)
        assertEquals(21.0 / 100.0 * 108.0, ribbon.textSize, 1e-9)
        assertTrue(2 * (safeRadius - ribbon.centreLineFromCentre) > 21.0 / 100.0 * 108.0)
    }
}
