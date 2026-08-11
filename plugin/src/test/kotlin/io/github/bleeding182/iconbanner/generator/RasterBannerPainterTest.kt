package io.github.bleeding182.iconbanner.generator

import io.github.bleeding182.iconbanner.api.BannerCorner
import io.github.bleeding182.iconbanner.api.BannerStyle
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Shape
import java.awt.image.BufferedImage
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The raster banner, asserted on pixels rather than against a checked-in PNG — a golden bitmap would
 * be hostage to the JDK's encoder, and the geometry is already pinned by [RibbonTest] and the vector
 * goldens. What is left to prove here is the compositing.
 *
 * Every sample point is derived from the default style on a 108px icon, where the top-left band covers
 * `x + y ∈ [62.78, 92.56]` and is centred on the pivot at `(38.83, 38.83)`. Points are chosen so the
 * whole pixel is covered: an antialiased edge pixel is a blend and has no exact colour to assert.
 */
class RasterBannerPainterTest {

    private val size = 108

    private val base = 0xFF1B5E20.toInt()

    // Off the fixture's own defaults rather than repeated here, so the two cannot drift apart.
    private val ribbonColor = assertNotNull(RasterIcon.parseColor(style().color)).rgb
    private val textColor = assertNotNull(RasterIcon.parseColor(style().textColor)).rgb

    /** On the band's centre line near its end: 15 units clear of both long edges, 47 past the text. */
    private val bandX = 72
    private val bandY = 5

    /** The icon's middle, which the band never reaches — its inner edge stops 15 units short. */
    private val middle = 54

    private fun painter(style: BannerStyle = style()) = BannerPainter(style, BannerText(testFont))

    private fun image(edge: Int = size, fill: Int = base): BufferedImage =
        BufferedImage(edge, edge, BufferedImage.TYPE_INT_ARGB).also { image ->
            for (y in 0 until edge) for (x in 0 until edge) image.setRGB(x, y, fill)
        }

    /**
     * An adaptive icon's `<foreground>`: a logo on a large transparent surround. The disc reaches the
     * band's inner edge, so a silhouette clip has *something* to paint and the test is not vacuous.
     */
    private fun logoOnTransparent(edge: Int = size): BufferedImage =
        BufferedImage(edge, edge, BufferedImage.TYPE_INT_ARGB).also { image ->
            image.createGraphics().apply {
                color = Color(base, true)
                fillOval(edge / 4, edge / 4, edge / 2, edge / 2)
                dispose()
            }
        }

    private fun BufferedImage.alphaAt(x: Int, y: Int): Int = getRGB(x, y) ushr 24

    private fun BufferedImage.rgbAt(x: Int, y: Int): Int = getRGB(x, y) and 0xFFFFFF

    private fun BufferedImage.count(predicate: (Int) -> Boolean): Int =
        (0 until height).sumOf { y -> (0 until width).count { x -> predicate(getRGB(x, y)) } }

    /** The ribbon exactly as the painter builds it, so a test can aim at the glyphs. */
    private fun ribbonOf(style: BannerStyle, edge: Int = size): Ribbon = Ribbon(
        viewportWidth = edge.toDouble(),
        viewportHeight = edge.toDouble(),
        corner = style.corner,
        positionPercent = style.positionPercent,
        maxTextSizePercent = style.maxTextSizePercent,
        lineHeight = style.lineHeight,
        textWidthPerCapHeight = BannerText(testFont).naturalWidthPerCapHeight(style.text),
    )

    private fun glyphsOf(style: BannerStyle, edge: Int = size): Shape =
        assertNotNull(BannerText(testFont).fit(style.text, ribbonOf(style, edge))).glyphs

    /** A pixel whose whole square is inside [shape] — never a half-covered edge of a stroke. */
    private fun pixelInside(shape: Shape): Pair<Int, Int> {
        val bounds = shape.bounds
        for (y in bounds.y..bounds.y + bounds.height) {
            for (x in bounds.x..bounds.x + bounds.width) {
                if (shape.contains(x.toDouble(), y.toDouble(), 1.0, 1.0)) return x to y
            }
        }
        error("No pixel lies wholly inside the glyphs, so no assertion here would mean anything")
    }

    @Test
    fun `a coloured banner fills the band, then the text, and leaves the icon's middle alone`() {
        val image = image()

        painter().paintColored(image, "ic_launcher.png", clipToSilhouette = false)

        assertEquals(ribbonColor, image.getRGB(bandX, bandY), "Band pixel is not the ribbon colour")
        val (glyphX, glyphY) = pixelInside(glyphsOf(style()))
        assertEquals(textColor, image.getRGB(glyphX, glyphY), "Glyph pixel is not the text colour")
        assertEquals(base, image.getRGB(middle, middle), "The band reached the middle of the icon")
    }

    @Test
    fun `the painted bitmap encodes to a PNG of the same size`() {
        val image = image()
        painter().paintColored(image, "ic_launcher.png", clipToSilhouette = false)

        val decoded = assertNotNull(RasterIcon.decode(RasterIcon.encode(image)))

        assertEquals(size, decoded.width)
        assertEquals(size, decoded.height)
        assertEquals(ribbonColor, decoded.getRGB(bandX, bandY))
    }

    /**
     * The standalone legacy icon: the launcher draws it unmasked, so the band must stop at the icon's
     * own silhouette instead of running out to the canvas corner.
     */
    @Test
    fun `clipping to the silhouette keeps transparent pixels transparent`() {
        val image = logoOnTransparent()

        painter().paintColored(image, "ic_launcher.webp", clipToSilhouette = true)

        assertEquals(0, image.alphaAt(bandX, bandY), "The band painted over a transparent pixel")
        assertTrue(
            image.count { it == ribbonColor } > 0,
            "Nothing was painted at all, so this proves nothing about the clip",
        )
    }

    /**
     * The trap the flag exists for: an adaptive foreground is mostly transparent, so clipping it to its
     * own alpha erases nearly the whole band. The system masks that layer, so it must not be clipped.
     */
    @Test
    fun `an adaptive foreground's band survives its transparent surround`() {
        val clipped = logoOnTransparent()
        val unclipped = logoOnTransparent()

        painter().paintColored(clipped, "ic_launcher_foreground.png", clipToSilhouette = true)
        painter().paintColored(unclipped, "ic_launcher_foreground.png", clipToSilhouette = false)

        assertEquals(ribbonColor, unclipped.getRGB(bandX, bandY), "The band did not reach the surround")
        assertTrue(
            unclipped.count { it == ribbonColor } > 3 * clipped.count { it == ribbonColor },
            "Clipping an adaptive foreground should cost most of the band, and here it barely did",
        )
    }

    @Test
    fun `two banners in opposite corners both land`() {
        val image = image()
        val second = "#FF2196F3"

        painter(style(corner = BannerCorner.TOP_LEFT)).paintColored(image, "icon", clipToSilhouette = false)
        painter(style(corner = BannerCorner.BOTTOM_RIGHT, color = second))
            .paintColored(image, "icon", clipToSilhouette = false)

        assertEquals(ribbonColor, image.getRGB(bandX, bandY))
        // The same point mirrored through the icon's centre.
        assertEquals(
            RasterIcon.parseColor(second)?.rgb,
            image.getRGB(size - 1 - bandX, size - 1 - bandY),
        )
    }

    @Test
    fun `the themed band is cleared, refilled at the style's alpha, and keeps the glyphs as holes`() {
        val style = style(monochromeAlphaPercent = 80.0)
        val image = image()

        painter(style).clipMonochrome(image, "mono.png")
        painter(style).punchMonochrome(image, "mono.png")

        assertEquals(204, image.alphaAt(bandX, bandY), "Band alpha is not 80% of 255")
        assertEquals(0xFFFFFF, image.rgbAt(bandX, bandY), "The system tints this layer, so it must be white")
        val (glyphX, glyphY) = pixelInside(glyphsOf(style))
        assertEquals(0, image.alphaAt(glyphX, glyphY), "The glyphs are not holes")
        assertEquals(base, image.getRGB(middle, middle), "The clip reached the middle of the icon")
    }

    /** One rounding rule for both icon forms: the vector's fill string is the reference. */
    @Test
    fun `the themed bitmap's alpha matches the themed vector's fill`() {
        val style = style(monochromeAlphaPercent = 37.5, text = "")
        val image = image()

        painter(style).clipMonochrome(image, "mono.png")
        painter(style).punchMonochrome(image, "mono.png")

        val vectorAlpha = BannerPainter.monochromeFill(37.5).substring(1, 3).toInt(16)
        assertEquals(vectorAlpha, image.alphaAt(bandX, bandY))
    }

    /**
     * Every band cleared before any band is filled. Interleaved, the second banner's clear would wipe
     * the first banner's fill wherever they overlap — and two bands in one corner always overlap.
     *
     * Both bands are textless, because this is about the clears and the fills. Positions 65 and 50 put
     * the bands over `x + y ∈ [62.78, 92.56]` and `[69.77, 99.55]`, so each has a stretch to itself.
     */
    @Test
    fun `every band is cleared before any band is filled`() {
        val outer = style(name = "outer", text = "", monochromeAlphaPercent = 40.0, positionPercent = 65.0)
        val inner = style(name = "inner", text = "", monochromeAlphaPercent = 60.0, positionPercent = 50.0)
        val image = image()

        listOf(outer, inner).forEach { painter(it).clipMonochrome(image, "mono.png") }
        listOf(outer, inner).forEach { painter(it).punchMonochrome(image, "mono.png") }

        // Sums of 66, 80 and 96 at the pixel's centre: outer only, both, inner only.
        assertEquals(102, image.alphaAt(61, 4), "The outer band alone should be at its own 40%")
        assertEquals(153, image.alphaAt(91, 4), "The inner band alone should be at its own 60%")
        assertTrue(
            image.alphaAt(75, 4) > 153,
            "The overlap is at most one band's alpha, so a clear ate a fill that was already there",
        )
        assertEquals(base, image.getRGB(middle, middle))
    }

    /**
     * Same style, two densities, same band — the whole reason a bitmap needs no sizing knob of its own.
     *
     * Measured as the *centre* of the band where it crosses the left edge column, which the text never
     * reaches. The centre rather than an edge: an antialiased edge bleeds a pixel outwards, which is
     * 2% of a 48px icon and 0.5% of a 192px one, and the two errors cancel in the middle.
     */
    @Test
    fun `the band lands in the same relative place at 48px and 192px`() {
        fun bandCentreAtLeftEdge(edge: Int): Double {
            val image = image(edge)
            painter().paintColored(image, "ic_launcher.png", clipToSilhouette = false)
            val touched = (0 until edge).filter { y -> image.getRGB(0, y) != base }
            assertTrue(touched.isNotEmpty(), "The band missed the left edge of a ${edge}px icon")
            return (touched.first() + touched.last() + 1) / 2.0 / edge
        }

        // Where the geometry says it should be, so this pins the place and not just the agreement.
        val expected = ribbonOf(style()).centreLineAxis / size
        assertEquals(expected, bandCentreAtLeftEdge(48), 0.02)
        assertEquals(expected, bandCentreAtLeftEdge(192), 0.02)
    }

    /**
     * A bitmap fill has to be resolved here, so a colour the plugin cannot parse itself is a failure
     * rather than a silent black band.
     */
    @Test
    fun `a colour a bitmap cannot be painted with fails`() {
        val failure = assertFailsWith<GeneratorFailure> {
            painter(style(color = "crimson"))
                .paintColored(image(), "mipmap-hdpi/ic_launcher.png", clipToSilhouette = false)
        }

        assertMessageContains(
            failure.message.orEmpty(),
            "mipmap-hdpi/ic_launcher.png",
            "crimson",
            "hex literal",
        )
    }

    /** `Clear` and `SrcAtop` on an image with no alpha channel paint black, and silently. */
    @Test
    fun `a bitmap without an alpha channel is refused`() {
        val opaque = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)

        assertFailsWith<GeneratorFailure> {
            painter().paintColored(opaque, "ic_launcher.png", clipToSilhouette = true)
        }
        assertFailsWith<GeneratorFailure> { painter().clipMonochrome(opaque, "ic_launcher.png") }
        assertFailsWith<GeneratorFailure> { painter().punchMonochrome(opaque, "ic_launcher.png") }
    }
}
