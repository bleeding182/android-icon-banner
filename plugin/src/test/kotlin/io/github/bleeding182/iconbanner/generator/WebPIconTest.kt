package io.github.bleeding182.iconbanner.generator

import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The case the whole raster feature exists for: a genuine `ic_launcher.webp` — the project's own
 * artwork — bannered and coming back out as a PNG of the same size.
 *
 * Hermetic despite the format, because the reader is a `testImplementation` dependency and so is
 * already on this JVM's classpath. What a *consuming* build gets instead is a classpath resolved and
 * registered at execution time, which is
 * [io.github.bleeding182.iconbanner.gradle.ImageReadersTest]'s subject.
 */
class WebPIconTest {

    private val ribbonColor = assertNotNull(RasterIcon.parseColor(style().color)).rgb

    private fun BufferedImage.pixelsColored(argb: Int): Int =
        (0 until width).sumOf { x -> (0 until height).count { y -> getRGB(x, y) == argb } }

    @Test
    fun `a webp launcher icon comes out bannered as a png of the same size`() {
        val source = assertNotNull(RasterIcon.decode(webpIcon.readBytes()), "The fixture is not a webp")
        assertEquals(72, source.width)
        assertEquals(72, source.height)
        assertEquals(0, source.pixelsColored(ribbonColor), "The source already has the ribbon's colour")

        val resources = FakeResources().raster("mipmap-hdpi/ic_launcher.webp", webpIcon.readBytes())

        val result = generate(request(resources)).success()

        // PNG, because the JDK has no WebP *writer*: same name, same qualifier folder, new extension.
        assertEquals(setOf("mipmap-hdpi/ic_launcher.png"), result.files.keys)
        val bytes = result.bytes("mipmap-hdpi/ic_launcher.png")
        assertEquals(
            listOf(0x89, 0x50, 0x4E, 0x47),
            bytes.take(4).map { it.toInt() and 0xFF },
            "Not a PNG signature",
        )

        val bannered = assertNotNull(RasterIcon.decode(bytes))
        assertEquals(source.width, bannered.width)
        assertEquals(source.height, bannered.height)
        // A count rather than one pixel: the band is clipped to this icon's own silhouette, so which
        // pixels it reaches is the artwork's business and only "the band was painted" is this test's.
        assertTrue(bannered.pixelsColored(ribbonColor) > 100, "No ribbon on the bannered icon")
    }
}
