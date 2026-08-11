package io.github.bleeding182.iconbanner.generator

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream
import javax.imageio.stream.MemoryCacheImageOutputStream

/**
 * The pixel end of the generator: bytes in, a mutable image out, and back to PNG bytes.
 *
 * Memory-cached streams throughout. `ImageIO`'s default is a temp-*file* cache, and a `@CacheableTask`
 * writing into `java.io.tmpdir` to composite a 192px icon is a surprise rather than a saving.
 */
internal object RasterIcon {

    /**
     * The bitmap as a mutable [BufferedImage.TYPE_INT_ARGB], converted unconditionally: a PNG may
     * decode as indexed or greyscale with no alpha channel, and both compositing modes the painter
     * uses work *through* the destination's alpha.
     *
     * Null when no registered reader can read the bytes, or when one tried and failed. The caller
     * answers by registering more readers and asking again, then by skipping the file with a warning.
     * The common case is not corruption: the JDK ships no WebP reader, and `ic_launcher.webp` is what
     * the Studio template emits.
     */
    fun decode(bytes: ByteArray): BufferedImage? {
        val stream = MemoryCacheImageInputStream(ByteArrayInputStream(bytes))
        val decoded = try {
            ImageIO.read(stream)
        } catch (_: Exception) {
            // Every exception, not just IOException: what a reader throws on a truncated file is its own
            // choice, and ArrayIndexOutOfBoundsException out of a frame parser is the realistic shape.
            // Nothing may escape, or the caller's skip-with-a-warning policy does not hold — and the
            // reader that threw may have come from the user's own configuration.
            //
            // `Error` still propagates: an OutOfMemoryError is not a verdict on the file.
            null
        } finally {
            // Not `use`: ImageIO.read closes the stream once a reader claims the bytes and leaves it
            // open when none does. A second close throws, and that IOException would land in the catch
            // above looking exactly like an unreadable bitmap.
            try {
                stream.close()
            } catch (_: IOException) {
            }
        } ?: return null

        // Inside the same net as the read: a reader can hand back an image the JDK then cannot convert
        // — a broken ICC profile survives `read` and throws CMMException here — and that is still a
        // decode that failed, not a build to kill with a stack trace.
        return try {
            val argb = BufferedImage(decoded.width, decoded.height, BufferedImage.TYPE_INT_ARGB)
            val graphics = argb.createGraphics()
            try {
                graphics.drawImage(decoded, 0, 0, null)
            } finally {
                graphics.dispose()
            }
            argb
        } catch (_: Exception) {
            null
        }
    }

    /**
     * PNG, whatever came in. The writer emits no timestamp, so the same image encodes to the same
     * bytes — which the generate task being a `@CacheableTask` depends on.
     */
    fun encode(image: BufferedImage): ByteArray {
        val bytes = ByteArrayOutputStream()
        MemoryCacheImageOutputStream(bytes).use { stream ->
            check(ImageIO.write(image, "png", stream)) { "This JVM has no PNG writer." }
            stream.flush()
        }
        return bytes.toByteArray()
    }

    /**
     * An Android colour literal as AWT: `#RGB`, `#ARGB`, `#RRGGBB`, `#AARRGGBB`, and nothing else.
     * Null for anything else.
     *
     * The short forms double each nibble, so `#ABC` is `#FFAABBCC`. That is **aapt2's** rule, which is
     * the one to follow: a vector hands the very same string to `android:fillColor` for the resource
     * compiler to expand, so both icon forms have to agree on what a colour means.
     * `android.graphics.Color.parseColor` rejects the short forms outright and is not the rule to copy.
     */
    fun parseColor(literal: String): Color? {
        if (!literal.startsWith('#')) return null
        val digits = literal.substring(1)
        // A regex rather than digitToIntOrNull: that accepts non-ASCII digits, which toLong then rejects.
        if (!digits.matches(HEX_DIGITS)) return null
        val expanded = when (digits.length) {
            3, 4 -> buildString { digits.forEach { append(it).append(it) } }
            6, 8 -> digits
            else -> return null
        }
        val argb = if (expanded.length == 6) "FF$expanded" else expanded
        return Color(argb.toLong(16).toInt(), true)
    }

    private val HEX_DIGITS = Regex("[0-9a-fA-F]+")
}
