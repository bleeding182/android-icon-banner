package io.github.bleeding182.iconbanner.generator

import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import java.awt.image.ColorModel
import java.awt.image.ComponentColorModel
import java.awt.image.DataBuffer
import java.io.IOException
import java.util.Locale
import javax.imageio.ImageReadParam
import javax.imageio.ImageReader
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadata
import javax.imageio.spi.IIORegistry
import javax.imageio.spi.ImageReaderSpi
import javax.imageio.stream.ImageInputStream

/**
 * An `ImageIO` reader that claims [MAGIC] and then fails however it is told to.
 *
 * It exists because the readers the plugin actually meets are all careful: every truncation and every
 * byte mutation of the checked-in webp comes back as an `IIOException`, so nothing real pins
 * [RasterIcon.decode]'s handling of a reader that throws something else. That handling is not
 * speculative — the extra readers are resolved from the *consuming* project's configuration, so a
 * version the plugin has never seen can be the one decoding — and a reader of our own is the only
 * honest way to hold it in place.
 */
internal object HostileImageReader {

    /** Eight bytes no real format claims, so this reader is the only one that ever answers for them. */
    val MAGIC: ByteArray = "ICONBANR".toByteArray()

    /**
     * An image the JDK can construct and cannot draw: its colour space throws on conversion, which is
     * what a broken or exotic ICC profile does — `CMMException`, from inside the colour pipeline rather
     * than from the reader. It gets a decode *past* `ImageIO.read` and fails in the ARGB conversion
     * after it, which is the one seam [RasterIcon.decode]'s own net has to cover too.
     */
    fun unconvertible(): BufferedImage {
        val space = object : ColorSpace(TYPE_RGB, 3) {
            override fun toRGB(colorvalue: FloatArray) = throw IllegalStateException("broken ICC profile")
            override fun fromRGB(rgbvalue: FloatArray) = throw IllegalStateException("broken ICC profile")
            override fun toCIEXYZ(colorvalue: FloatArray) = throw IllegalStateException("broken ICC profile")
            override fun fromCIEXYZ(colorvalue: FloatArray) = throw IllegalStateException("broken ICC profile")
        }
        val model = ComponentColorModel(space, false, false, ColorModel.OPAQUE, DataBuffer.TYPE_BYTE)
        return BufferedImage(model, model.createCompatibleWritableRaster(8, 8), false, null)
    }

    /**
     * Runs [body] with the reader registered, and with [reading] as what its `read` does. Deregistered
     * afterwards, without fail: `IIORegistry` is per thread group and outlives the test.
     */
    fun registered(
        reading: () -> BufferedImage = { throw ArrayIndexOutOfBoundsException("Index 280 out of bounds for length 280") },
        body: () -> Unit,
    ) {
        val registry = IIORegistry.getDefaultInstance()
        val spi = Spi(reading)
        registry.registerServiceProvider(spi, ImageReaderSpi::class.java)
        try {
            body()
        } finally {
            registry.deregisterServiceProvider(spi, ImageReaderSpi::class.java)
        }
    }

    /** The long constructor rather than the protected fields: those collide with their own getters. */
    private class Spi(private val reading: () -> BufferedImage) : ImageReaderSpi(
        "icon banner tests",
        "1",
        arrayOf("hostile"),
        arrayOf("hostile"),
        arrayOf("image/x-hostile"),
        Reader::class.java.name,
        arrayOf(ImageInputStream::class.java),
        null,
        false, null, null, null, null,
        false, null, null, null, null,
    ) {

        override fun canDecodeInput(source: Any?): Boolean {
            val stream = source as? ImageInputStream ?: return false
            val head = ByteArray(MAGIC.size)
            stream.mark()
            return try {
                stream.readFully(head)
                head.contentEquals(MAGIC)
            } catch (e: IOException) {
                false
            } finally {
                stream.reset()
            }
        }

        override fun createReaderInstance(extension: Any?): ImageReader = Reader(this, reading)

        override fun getDescription(locale: Locale?): String = "Claims a signature and then fails"
    }

    private class Reader(spi: ImageReaderSpi, private val reading: () -> BufferedImage) : ImageReader(spi) {
        override fun getNumImages(allowSearch: Boolean): Int = 1

        override fun getWidth(imageIndex: Int): Int = 8

        override fun getHeight(imageIndex: Int): Int = 8

        override fun getImageTypes(imageIndex: Int): Iterator<ImageTypeSpecifier> =
            listOf(ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_ARGB)).iterator()

        override fun getStreamMetadata(): IIOMetadata? = null

        override fun getImageMetadata(imageIndex: Int): IIOMetadata? = null

        override fun read(imageIndex: Int, param: ImageReadParam?): BufferedImage = reading()
    }
}
