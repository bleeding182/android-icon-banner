package io.github.bleeding182.iconbanner.generator

import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Transparency
import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import java.awt.image.ComponentColorModel
import java.awt.image.DataBuffer
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Decoding, encoding and colour parsing. No checked-in bitmaps: every source image is built here, so
 * nothing in the suite is hostage to what a particular JDK's PNG encoder happens to emit.
 */
class RasterIconTest {

    private fun png(image: BufferedImage): ByteArray = RasterIcon.encode(image)

    @Test
    fun `an ARGB bitmap survives a round trip`() {
        val source = BufferedImage(6, 4, BufferedImage.TYPE_INT_ARGB)
        source.setRGB(0, 0, 0x80FF0000.toInt())
        source.setRGB(5, 3, 0xFF00FF00.toInt())

        val decoded = assertNotNull(RasterIcon.decode(png(source)))

        assertEquals(6, decoded.width)
        assertEquals(4, decoded.height)
        assertEquals(0x80FF0000.toInt(), decoded.getRGB(0, 0))
        assertEquals(0xFF00FF00.toInt(), decoded.getRGB(5, 3))
    }

    /**
     * The reason the conversion is unconditional: a greyscale or indexed PNG decodes with no alpha
     * channel, and `Clear` and `SrcAtop` on such an image paint opaque black instead of nothing.
     */
    @Test
    fun `a greyscale bitmap decodes as ARGB with a real alpha channel`() {
        val grey = BufferedImage(4, 4, BufferedImage.TYPE_BYTE_GRAY)

        val decoded = assertNotNull(RasterIcon.decode(png(grey)))

        assertEquals(BufferedImage.TYPE_INT_ARGB, decoded.type)
        assertTrue(decoded.colorModel.hasAlpha(), "Decoded bitmap must carry an alpha channel")
        assertEquals(255, Color(decoded.getRGB(0, 0), true).alpha)
    }

    /**
     * A grey+alpha PNG (IHDR colour type 4) decodes through ImageIO as a linear `CS_GRAY` image, and
     * `drawImage` would convert that to sRGB on the way to ARGB: 128 came out as 0xBC, every grey
     * pixel visibly lighter than the file says. Grey *without* alpha decodes as a plain
     * `TYPE_BYTE_GRAY` and never had the problem.
     */
    @Test
    fun `a grey and alpha bitmap keeps its grey values`() {
        // 257 turns 0x80 into 0x8080: the same value at 16 bits.
        listOf(Triple(DataBuffer.TYPE_BYTE, 8, 1), Triple(DataBuffer.TYPE_USHORT, 16, 257)).forEach { (transferType, bits, scale) ->
            val bytes = png(greyAlpha(transferType, listOf(0x80 * scale to 0xFF * scale, 0x80 * scale to 0x80 * scale)))

            // Proves the fixture: colour type 4 at the bit depth asked for, not something ImageIO chose.
            assertEquals(bits, bytes[24].toInt(), "IHDR bit depth")
            assertEquals(4, bytes[25].toInt(), "IHDR colour type")

            val decoded = assertNotNull(RasterIcon.decode(bytes))
            assertEquals(BufferedImage.TYPE_INT_ARGB, decoded.type)
            assertEquals(0xFF808080.toInt(), decoded.getRGB(0, 0), "$bits-bit opaque grey")
            assertEquals(0x80808080.toInt(), decoded.getRGB(1, 0), "$bits-bit translucent grey")
        }
    }

    /** One row of (grey, alpha) samples, in the colour model ImageIO writes as PNG colour type 4. */
    private fun greyAlpha(transferType: Int, pixels: List<Pair<Int, Int>>): BufferedImage {
        val model = ComponentColorModel(
            ColorSpace.getInstance(ColorSpace.CS_GRAY),
            true,
            false,
            Transparency.TRANSLUCENT,
            transferType,
        )
        val raster = model.createCompatibleWritableRaster(pixels.size, 1)
        pixels.forEachIndexed { x, (grey, alpha) -> raster.setPixel(x, 0, intArrayOf(grey, alpha)) }
        return BufferedImage(model, raster, false, null)
    }

    @Test
    fun `an indexed bitmap decodes as ARGB too`() {
        val indexed = BufferedImage(4, 4, BufferedImage.TYPE_BYTE_INDEXED)

        val decoded = assertNotNull(RasterIcon.decode(png(indexed)))

        assertEquals(BufferedImage.TYPE_INT_ARGB, decoded.type)
    }

    @Test
    fun `bytes no reader understands decode to null instead of throwing`() {
        assertNull(RasterIcon.decode("this is not an image".toByteArray()))
        assertNull(RasterIcon.decode(ByteArray(0)))
    }

    /** A PNG header with the pixels cut off: malformed input throws inside ImageIO, and must not here. */
    @Test
    fun `a truncated bitmap decodes to null instead of throwing`() {
        val truncated = png(BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)).copyOf(20)

        assertNull(RasterIcon.decode(truncated))
    }

    /**
     * A signature a reader claims, followed by nothing it can use — the case where a reader is selected
     * and then fails, rather than none claiming the bytes at all. Both a PNG header over rubbish and a
     * real webp cut short: the webp reader is a `testImplementation` dependency, so this is the
     * third-party path the widened catch was written for.
     */
    @Test
    fun `a claimed signature over rubbish decodes to null instead of throwing`() {
        val pngHeader = png(BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)).copyOf(8)

        assertNull(RasterIcon.decode(pngHeader + ByteArray(200) { 0x33 }))
        assertNull(RasterIcon.decode(webpIcon.readBytes().copyOf(64)))
        assertNull(RasterIcon.decode(webpIcon.readBytes().copyOf(200)))
    }

    /**
     * The reason the catch is `Exception` and not `IOException`. A reader that reports malformed input
     * by throwing out of its own arithmetic is not hypothetical — the extra readers are resolved from
     * the consuming project's configuration, so they are code this plugin does not control — but the
     * pinned webp reader happens to be careful and throws `IIOException` for every truncation and
     * mutation tried. A reader of our own is therefore what pins the behaviour.
     */
    @Test
    fun `a reader that throws a RuntimeException decodes to null instead of propagating`() {
        val bytes = HostileImageReader.MAGIC + ByteArray(64)

        HostileImageReader.registered {
            assertNull(RasterIcon.decode(bytes))
        }
    }

    /**
     * The decode is two steps and the net has to cover both. A reader can return an image the JDK then
     * cannot convert to ARGB — a broken ICC profile survives `read` and throws out of the colour
     * pipeline — and that is still a file the plugin could not decode, not a build to kill. The catch
     * used to stop at `ImageIO.read`, so this escaped as a stack trace.
     */
    @Test
    fun `an image that cannot be converted to ARGB decodes to null instead of propagating`() {
        val bytes = HostileImageReader.MAGIC + ByteArray(64)

        HostileImageReader.registered(reading = { HostileImageReader.unconvertible() }) {
            assertNull(RasterIcon.decode(bytes))
        }
    }

    /** An `Error` is not a verdict on the file, so it must not be mistaken for an undecodable one. */
    @Test
    fun `an Error out of a reader still propagates`() {
        val bytes = HostileImageReader.MAGIC + ByteArray(64)

        HostileImageReader.registered(reading = { throw StackOverflowError("from the reader") }) {
            assertFailsWith<StackOverflowError> { RasterIcon.decode(bytes) }
        }
    }

    /**
     * WebP is what the Studio template emits, and the JDK ships no reader for it — this is the case
     * the caller's skip-with-a-warning path exists for, not corruption.
     */
    @Test
    fun `a webp decodes to null because the JDK has no reader for it`() {
        val header = "RIFF".toByteArray() + byteArrayOf(0x1A, 0, 0, 0) +
            "WEBPVP8L".toByteArray() + ByteArray(16)

        assertNull(RasterIcon.decode(header))
    }

    /** The generate task is a `@CacheableTask`: identical input has to produce identical bytes. */
    @Test
    fun `encoding the same bitmap twice gives identical bytes`() {
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        image.createGraphics().apply {
            color = Color(0xE9, 0x1E, 0x63, 0xC0)
            fillRect(2, 2, 9, 7)
            dispose()
        }

        assertTrue(png(image).contentEquals(png(image)), "PNG encoding is not byte-deterministic")
    }

    @Test
    fun `the encoding is a PNG`() {
        val magic = png(BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)).take(8)

        assertEquals(listOf(-119, 80, 78, 71, 13, 10, 26, 10).map { it.toByte() }, magic)
    }

    @Test
    fun `the long colour forms parse as written`() {
        assertEquals(Color(0xE9, 0x1E, 0x63, 0xFF), RasterIcon.parseColor("#FFE91E63"))
        assertEquals(Color(0xE9, 0x1E, 0x63, 0x80), RasterIcon.parseColor("#80E91E63"))
        // No alpha given is opaque, as aapt2 reads it.
        assertEquals(Color(0xE9, 0x1E, 0x63, 0xFF), RasterIcon.parseColor("#E91E63"))
        assertEquals(Color(0xE9, 0x1E, 0x63, 0xFF), RasterIcon.parseColor("#e91e63"))
    }

    /** aapt2 doubles each nibble. `android.graphics.Color.parseColor` rejects these outright. */
    @Test
    fun `the short colour forms double each nibble`() {
        assertEquals(RasterIcon.parseColor("#FFAABBCC"), RasterIcon.parseColor("#ABC"))
        assertEquals(RasterIcon.parseColor("#88AABBCC"), RasterIcon.parseColor("#8ABC"))
        assertEquals(Color(0, 0, 0, 255), RasterIcon.parseColor("#000"))
        assertEquals(Color(255, 255, 255, 0), RasterIcon.parseColor("#0FFF"))
    }

    @Test
    fun `anything that is not a hex literal is rejected`() {
        listOf(
            "?attr/colorPrimary",
            "FFE91E63",
            "#",
            "#AB",
            "#ABCDE",
            "#ABCDEFA",
            "#ABCDEFABC",
            "#GGHHII",
            "#FFE91E6 ",
            "red",
            "",
        ).forEach { assertNull(RasterIcon.parseColor(it), "Expected \"$it\" to be rejected") }
    }
}
