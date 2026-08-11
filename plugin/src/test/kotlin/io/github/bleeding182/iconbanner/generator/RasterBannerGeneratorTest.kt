package io.github.bleeding182.iconbanner.generator

import io.github.bleeding182.iconbanner.api.GenerationResult
import io.github.bleeding182.iconbanner.api.ImageCodecs
import io.github.bleeding182.iconbanner.api.ResourceRef
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The *walk* over a bitmap-backed icon: which file each case produces, where it lands and what the
 * banner was clipped to. The compositing itself belongs to [RasterBannerPainterTest], so the
 * assertions here stop at one pixel on the band — enough to tell a painted band from a missing one.
 *
 * Source bitmaps are built rather than checked in, and a fixture called `.webp` in fact holds PNG
 * bytes — the JDK ships no WebP reader, and whether a file is a bitmap at all comes from the lookup
 * rather than from its name. The extension still matters to the *output* name, which is why the
 * fixtures wear one.
 */
class RasterBannerGeneratorTest {

    private val ribbonColor = assertNotNull(RasterIcon.parseColor(style().color)).rgb

    private val roundIconRef = ResourceRef("mipmap", "ic_launcher_round")

    /**
     * A pixel on the band, clear of both its edges and of the text, as a fraction of the icon. The
     * geometry is proportional, so the one point serves every density: on a 108px icon it is (72, 5),
     * which [RasterBannerPainterTest] derives from the default band's `x + y ∈ [62.78, 92.56]`.
     */
    private fun bandPixel(edge: Int): Pair<Int, Int> = edge * 72 / 108 to edge * 5 / 108

    /** An adaptive `<foreground>` as they actually come: a logo floating on a transparent surround. */
    private fun logoOnTransparentPng(edge: Int): ByteArray {
        val image = BufferedImage(edge, edge, BufferedImage.TYPE_INT_ARGB)
        image.createGraphics().apply {
            color = Color(0xFF3DDC84.toInt(), true)
            fillOval(edge / 4, edge / 4, edge / 2, edge / 2)
            dispose()
        }
        return ByteArrayOutputStream().use { bytes ->
            ImageIO.write(image, "png", bytes)
            bytes.toByteArray()
        }
    }

    /** The generated bitmap at [path], decoded — the bytes themselves are the encoder's business. */
    private fun GenerationResult.Success.image(path: String): BufferedImage =
        assertNotNull(RasterIcon.decode(bytes(path)), "$path did not come out as a decodable image")

    private fun assertBandPainted(image: BufferedImage, path: String) {
        val (x, y) = bandPixel(image.width)
        assertEquals(ribbonColor, image.getRGB(x, y), "No band at ($x, $y) in $path")
    }


    /**
     * The warning set folds duplicates by string, which only works if the string does not carry the
     * icon's own units. A vector's every qualifier variant declares one viewport, but five densities of
     * a bitmap are five different pixel sizes — so a cap height or a viewport in the message made one
     * complaint five, and a real project ten or twenty across icon, roundIcon and both banners. Even in
     * dp the figure drifts with the pixel grid, so the painter tracks whether it has complained instead.
     */
    @Test
    fun `one illegible text on a bitmap warns once, not once per density`() {
        val resources = FakeResources()
            .raster("mipmap-mdpi/ic_launcher.png", solidPng(48))
            .raster("mipmap-hdpi/ic_launcher.png", solidPng(72))
            .raster("mipmap-xhdpi/ic_launcher.png", solidPng(96))
            .raster("mipmap-xxhdpi/ic_launcher.png", solidPng(144))
            .raster("mipmap-xxxhdpi/ic_launcher.png", solidPng(192))

        val result = generate(request(resources, style(text = "STAGING RC1"))).success()

        assertEquals(1, result.warnings.size, result.warnings.toString())
        // The icon's own units are what varied; nothing in the message may quote them.
        assertTrue("viewport" !in result.warnings.single(), result.warnings.single())
    }

    @Test
    fun `a raster launcher icon is bannered in each of its density folders`() {
        val resources = FakeResources()
            .raster("mipmap-hdpi/ic_launcher.webp", solidPng(72))
            .raster("mipmap-xxhdpi/ic_launcher.webp", solidPng(144))

        val result = generate(request(resources)).success()

        // PNG whatever went in, under the source's own qualifiers: writing the wrong folder is silent
        // at the AGP level, and the original would simply be packaged instead.
        assertEquals(
            setOf("mipmap-hdpi/ic_launcher.png", "mipmap-xxhdpi/ic_launcher.png"),
            result.files.keys,
        )
        assertBandPainted(result.image("mipmap-hdpi/ic_launcher.png"), "mipmap-hdpi")
        assertBandPainted(result.image("mipmap-xxhdpi/ic_launcher.png"), "mipmap-xxhdpi")
        assertEquals(72, result.image("mipmap-hdpi/ic_launcher.png").width)
        assertEquals(144, result.image("mipmap-xxhdpi/ic_launcher.png").width)
    }

    @Test
    fun `a raster round icon is bannered too`() {
        // The round-icon fallback counts a raster as present, so such a project reaches the generator.
        val resources = FakeResources()
            .raster("mipmap-hdpi/ic_launcher.webp", solidPng(72))
            .raster("mipmap-hdpi/ic_launcher_round.webp", solidPng(72))

        val result = generate(request(resources, roundIcon = roundIconRef)).success()

        assertEquals(
            setOf("mipmap-hdpi/ic_launcher.png", "mipmap-hdpi/ic_launcher_round.png"),
            result.files.keys,
        )
        assertBandPainted(result.image("mipmap-hdpi/ic_launcher_round.png"), "round")
    }

    @Test
    fun `the Android Studio shape produces both the vector and the raster outputs`() {
        // An adaptive icon in anydpi-v26 with legacy mipmaps beside it for API 24 and 25.
        val resources = FakeResources()
            .xml(ADAPTIVE_PATH, input("adaptive_shared_mono.xml"))
            .xml(FOREGROUND_PATH, input("foreground.xml"))
            .raster("mipmap-hdpi/ic_launcher.webp", solidPng(72))
            .raster("mipmap-xxhdpi/ic_launcher.webp", solidPng(144))

        val result = generate(request(resources)).success()

        assertEquals(
            setOf(
                ADAPTIVE_PATH,
                FOREGROUND_PATH,
                MONO_PATH,
                "mipmap-hdpi/ic_launcher.png",
                "mipmap-xxhdpi/ic_launcher.png",
            ),
            result.files.keys,
        )
        assertBandPainted(result.image("mipmap-hdpi/ic_launcher.png"), "mipmap-hdpi")
    }

    @Test
    fun `a standalone raster icon's band is clipped to its own silhouette`() {
        // A launcher draws a legacy icon unmasked, so a band running out to the canvas corner would
        // extend a round icon's silhouette with a floating triangle.
        val resources = FakeResources()
            .raster("mipmap-hdpi/ic_launcher.webp", logoOnTransparentPng(108))

        val result = generate(request(resources)).success()

        val (x, y) = bandPixel(108)
        assertEquals(0, result.image("mipmap-hdpi/ic_launcher.png").getRGB(x, y) ushr 24)
    }


    @Test
    fun `an adaptive foreground's band survives its transparent surround`() {
        // The opposite clip from a standalone icon, and the reason the caller has to decide: the system
        // masks this layer, and clipping the band to a mostly transparent foreground would erase it.
        val resources = FakeResources()
            .xml(ADAPTIVE_PATH, input("adaptive_no_mono.xml"))
            .raster("drawable-hdpi/ic_launcher_foreground.png", logoOnTransparentPng(108))

        val result = generate(request(resources)).success()

        assertEquals(setOf("drawable-hdpi/ic_launcher_foreground.png"), result.files.keys)
        assertBandPainted(result.image("drawable-hdpi/ic_launcher_foreground.png"), "foreground")
    }

    @Test
    fun `a shared foreground and monochrome bitmap gets the reserved png name and a redirect`() {
        val resources = FakeResources()
            .xml(ADAPTIVE_PATH, input("adaptive_shared_mono.xml"))
            .raster("drawable-hdpi/ic_launcher_foreground.png", logoOnTransparentPng(108))

        val result = generate(request(resources)).success()

        val mono = "drawable-hdpi/ic_launcher_foreground${DefaultBannerGenerator.MONOCHROME_SUFFIX}.png"
        assertEquals(
            setOf(ADAPTIVE_PATH, "drawable-hdpi/ic_launcher_foreground.png", mono),
            result.files.keys,
        )
        assertTrue(
            "@drawable/ic_launcher_foreground_iconbanner_mono" in result.xml(ADAPTIVE_PATH),
            result.xml(ADAPTIVE_PATH),
        )
        // The themed band: cleared, then filled back in at the style's alpha in white for the system
        // to tint. The coloured copy is a different file, so the two cannot be the same bytes.
        val (x, y) = bandPixel(108)
        assertEquals(0xFFFFFFFF.toInt(), result.image(mono).getRGB(x, y))
    }

    @Test
    fun `two banners on a monochrome bitmap are both cleared before either is filled`() {
        // Interleaved, the second band's clear would eat the first band's fill. Positions 65 and 50 put
        // the two bands over stretches of the diagonal that each have a piece to themselves.
        val outer = style(name = "outer", text = "", monochromeAlphaPercent = 40.0, positionPercent = 65.0)
        val inner = style(name = "inner", text = "", monochromeAlphaPercent = 60.0, positionPercent = 50.0)
        val resources = FakeResources()
            .xml(ADAPTIVE_PATH, input("adaptive_shared_mono.xml"))
            .raster("drawable-hdpi/ic_launcher_foreground.png", solidPng(108))

        val result = generate(request(resources, outer, inner)).success()

        val mono = result.image(
            "drawable-hdpi/ic_launcher_foreground${DefaultBannerGenerator.MONOCHROME_SUFFIX}.png",
        )
        // Sums of 66, 80 and 96 at the pixel's centre: outer only, both, inner only.
        assertEquals(102, mono.getRGB(61, 4) ushr 24, "The outer band is not at its own 40%")
        assertEquals(153, mono.getRGB(91, 4) ushr 24, "The inner band is not at its own 60%")
        assertTrue(
            mono.getRGB(75, 4) ushr 24 > 153,
            "The overlap is at most one band's alpha, so a clear ate a fill already there",
        )
    }


    @Test
    fun `an undecodable bitmap beside a vector warns and the vector is still bannered`() {
        val resources = FakeResources()
            .xml("drawable/ic_launcher.xml", input("foreground.xml"))
            .raster("drawable-hdpi/ic_launcher.png", bytes = "not an image".toByteArray())

        val result = generate(request(resources, icon = DRAWABLE_ICON)).success()

        assertEquals(setOf("drawable/ic_launcher.xml"), result.files.keys)
        val warning = result.warnings.single()
        assertMessageContains(warning, "drawable-hdpi/ic_launcher.png", "no image reader could decode it")
        // Not a word about WebP: the Gradle layer registers a reader for it, and it normally decodes.
        assertTrue("webp" !in warning.lowercase(), warning)
    }

    @Test
    fun `a nine-patch is skipped with a warning rather than composited over its border`() {
        val resources = FakeResources()
            .raster("mipmap-hdpi/ic_launcher.9.png", solidPng(72))
            .raster("mipmap-xxhdpi/ic_launcher.webp", solidPng(144))

        val result = generate(request(resources)).success()

        assertEquals(setOf("mipmap-xxhdpi/ic_launcher.png"), result.files.keys)
        assertMessageContains(result.warnings.single(), "mipmap-hdpi/ic_launcher.9.png", "nine-patch")
    }

    @Test
    fun `a resource whose every file is skipped fails and says why for each`() {
        val resources = FakeResources()
            .raster("mipmap-hdpi/ic_launcher.9.png", solidPng(72))
            .raster("mipmap-xxhdpi/ic_launcher.webp", bytes = "not an image".toByteArray())

        val message = generate(request(resources)).failureMessage()

        // A Failure carries no warnings, so the reasons have to travel in the message itself.
        assertMessageContains(
            message,
            "Launcher icon @mipmap/ic_launcher",
            "mipmap-hdpi/ic_launcher.9.png (it is a nine-patch)",
            "mipmap-xxhdpi/ic_launcher.webp (no image reader could decode it)",
        )
    }


    @Test
    fun `the info note names both paths when the extension changed, and one when it did not`() {
        // The override is silent at the AGP level, so this list is the only notice a user gets.
        val webp = FakeResources().raster("mipmap-hdpi/ic_launcher.webp", solidPng(72))
        val png = FakeResources().raster("mipmap-hdpi/ic_launcher.png", solidPng(72))

        assertEquals(
            listOf("mipmap-hdpi/ic_launcher.webp replaced by a bannered copy at mipmap-hdpi/ic_launcher.png"),
            generate(request(webp)).success().info,
        )
        assertEquals(
            listOf("mipmap-hdpi/ic_launcher.png replaced by a bannered copy"),
            generate(request(png)).success().info,
        )
    }


    @Test
    fun `the extra image readers are asked for once, and named the file the JDK failed on`() {
        var calls = 0
        var named: String? = null
        val resources = FakeResources()
            .raster("mipmap-hdpi/ic_launcher.png", bytes = "not an image".toByteArray())
            .raster("mipmap-xxhdpi/ic_launcher.png", bytes = "not an image either".toByteArray())

        val codecs = ImageCodecs { path ->
            calls++
            named = path
        }

        // A failure, because every file was skipped — the codec seam is what this case is about.
        generate(request(resources, codecs = codecs)).failureMessage()

        // Once for two undecodable files: the second decode cannot fare better than the first, so
        // there is nothing to register a second time.
        assertEquals(1, calls)
        assertEquals("mipmap-hdpi/ic_launcher.png", named)
    }

    @Test
    fun `a bitmap the JDK reads never asks for them`() {
        // The Gradle layer resolves a dependency to satisfy this, and a project whose legacy icons are
        // PNG has no use for a WebP reader — it would fail on a repository it never needed.
        var calls = 0
        val resources = FakeResources()
            .raster("mipmap-hdpi/ic_launcher.png", solidPng(72))
            .raster("mipmap-xxhdpi/ic_launcher.png", solidPng(144))

        val result = generate(request(resources, codecs = ImageCodecs { _ -> calls++ })).success()

        assertEquals(
            setOf("mipmap-hdpi/ic_launcher.png", "mipmap-xxhdpi/ic_launcher.png"),
            result.files.keys,
        )
        assertEquals(0, calls)
    }

    @Test
    fun `a vector-only icon never asks for them`() {
        var calls = 0
        val resources = FakeResources()
            .xml(ADAPTIVE_PATH, input("adaptive_shared_mono.xml"))
            .xml(FOREGROUND_PATH, input("foreground.xml"))

        generate(request(resources, codecs = ImageCodecs { _ -> calls++ })).success()

        assertEquals(0, calls)
    }


    @Test
    fun `a bannered bitmap encodes to the same bytes across runs`() {
        // The generate task is cacheable, which needs the PNG bytes to be stable and not merely equal
        // as images.
        fun run() = generate(
            request(FakeResources().raster("mipmap-hdpi/ic_launcher.webp", solidPng(72)))
        ).success()

        assertSameFiles(run(), run())
    }
}
