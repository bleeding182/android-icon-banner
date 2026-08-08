package com.github.bleeding182.iconbanner.generator

import com.github.bleeding182.iconbanner.api.BannerCorner
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Geometry in isolation. The golden files cover how the ribbon looks in a real vector; these cover
 * the arithmetic, where an off-by-one in a corner table is invisible in a diff of glyph outlines.
 */
class RibbonTest {

    private fun ribbon(
        corner: BannerCorner,
        width: Double = 108.0,
        height: Double = 108.0,
        heightPercent: Double = 20.0,
    ) = Ribbon(width, height, corner, heightPercent)

    @Test
    fun `reach is three quarters of the shorter edge`() {
        assertEquals(81.0, ribbon(BannerCorner.TOP_LEFT).reach)
        assertEquals(18.0, ribbon(BannerCorner.TOP_LEFT, width = 24.0, height = 24.0).reach)
    }

    @Test
    fun `band width is the configured percentage of the shorter edge`() {
        assertEquals(21.6, ribbon(BannerCorner.TOP_LEFT).bandWidth, 1e-9)
        assertEquals(4.8, ribbon(BannerCorner.TOP_LEFT, width = 24.0, height = 24.0).bandWidth, 1e-9)
    }

    @Test
    fun `a non-square viewport normalises against the shorter edge`() {
        // Same band on a tall icon as on a square one of the same width, so the setting stays
        // portable between projects with differently shaped foregrounds.
        val tall = ribbon(BannerCorner.TOP_LEFT, width = 108.0, height = 200.0)
        assertEquals(81.0, tall.reach)
        assertEquals(21.6, tall.bandWidth, 1e-9)
    }

    @Test
    fun `top left quad runs from the x axis to the y axis`() {
        assertEquals(
            "M 81 0 L 0 81 L 0 59.4 L 59.4 0 Z",
            ribbon(BannerCorner.TOP_LEFT).quadPathData(),
        )
    }

    @Test
    fun `top right quad is mirrored into the right edge`() {
        assertEquals(
            "M 27 0 L 108 81 L 108 59.4 L 48.6 0 Z",
            ribbon(BannerCorner.TOP_RIGHT).quadPathData(),
        )
    }

    @Test
    fun `bottom left quad is mirrored into the bottom edge`() {
        assertEquals(
            "M 0 27 L 81 108 L 59.4 108 L 0 48.6 Z",
            ribbon(BannerCorner.BOTTOM_LEFT).quadPathData(),
        )
    }

    @Test
    fun `bottom right quad is mirrored into both edges`() {
        assertEquals(
            "M 27 108 L 108 27 L 108 48.6 L 48.6 108 Z",
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
    fun `pivot is the centre of the band`() {
        val topLeft = ribbon(BannerCorner.TOP_LEFT)
        assertEquals(35.1, topLeft.pivotX, 1e-9)
        assertEquals(35.1, topLeft.pivotY, 1e-9)

        val bottomRight = ribbon(BannerCorner.BOTTOM_RIGHT)
        assertEquals(108.0 - 35.1, bottomRight.pivotX, 1e-9)
        assertEquals(108.0 - 35.1, bottomRight.pivotY, 1e-9)
    }

    @Test
    fun `text budget leaves padding on every side`() {
        val ribbon = ribbon(BannerCorner.TOP_LEFT)
        val padding = 0.18 * 21.6
        assertEquals(21.6 - 2 * padding, ribbon.availableTextHeight, 1e-9)
        assertEquals((81.0 - 10.8) * Math.sqrt(2.0) - 2 * padding, ribbon.availableTextLength, 1e-9)
    }

    @Test
    fun `inverse clip is the corner triangle plus the rest of the icon`() {
        assertEquals(
            "M 0 0 L 59.4 0 L 0 59.4 Z M 81 0 L 108 0 L 108 108 L 0 108 L 0 81 Z",
            ribbon(BannerCorner.TOP_LEFT).inverseClipPathData(),
        )
        assertEquals(
            "M 108 0 L 48.6 0 L 108 59.4 Z M 27 0 L 0 0 L 0 108 L 108 108 L 108 81 Z",
            ribbon(BannerCorner.TOP_RIGHT).inverseClipPathData(),
        )
        assertEquals(
            "M 0 108 L 59.4 108 L 0 48.6 Z M 0 27 L 0 0 L 108 0 L 108 108 L 81 108 Z",
            ribbon(BannerCorner.BOTTOM_LEFT).inverseClipPathData(),
        )
        assertEquals(
            "M 108 108 L 48.6 108 L 108 48.6 Z M 27 108 L 0 108 L 0 0 L 108 0 L 108 27 Z",
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
