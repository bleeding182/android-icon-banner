package io.github.bleeding182.iconbanner.generator

import java.awt.Font
import java.awt.Shape
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.awt.geom.PathIterator
import java.io.File
import java.util.Locale

/**
 * Text drawn into a ribbon: the finished `pathData`, and the same glyphs as an AWT [Shape] for the
 * raster path.
 *
 * [pathData] is *not* derived from [glyphs]. Both carry the same transform, but the golden files pin
 * every coordinate in [pathData], so routing it through a second shape would let a rounding
 * difference rewrite all of them silently.
 */
internal data class FittedText(val pathData: String, val glyphs: Shape)

/**
 * Turns text into VectorDrawable `pathData` using the JDK's own font support.
 *
 * `GlyphVector.getOutline()` keeps quadratic and cubic segments rather than flattening them, and
 * its baseline-at-zero, y-down convention is already the VectorDrawable one. No axis flip.
 */
internal class BannerText(fontFile: File) {

    /**
     * Antialiasing and fractional metrics on. Fractional metrics in particular keeps advances off
     * the integer grid, so glyph positions scale linearly with size instead of snapping.
     */
    private val renderContext = FontRenderContext(null, true, true)

    /**
     * Read through a stream, never as a [File]. `Font.createFont(TRUETYPE_FONT, File)` keeps the
     * file open for as long as the `Font` lives, and the JVM here is the Gradle daemon: on Windows
     * the next build then cannot delete the font out of the font task's output directory, and the
     * build fails before it starts. The stream variant copies into the JDK's own temp file instead.
     */
    val font: Font = fontFile.inputStream().buffered().use { Font.createFont(Font.TRUETYPE_FONT, it) }

    /**
     * The first character [font] cannot draw. Missing-glyph boxes on a launcher icon are worse than
     * a build error, so the caller fails.
     */
    fun firstUndisplayableCharacter(text: String): String? {
        val index = font.canDisplayUpTo(text)
        if (index < 0) return null
        val codePoint = text.codePointAt(index)
        val rendered = String(Character.toChars(codePoint))
        return String.format(Locale.ROOT, "'%s' (U+%04X)", rendered, codePoint)
    }

    /**
     * Width per unit of cap height — what [Ribbon] needs to size the band. Scale-free, so measuring
     * once at the reference size is enough. "Cap height" is this string's ink height.
     */
    fun naturalWidthPerCapHeight(text: String): Double? {
        if (text.isEmpty()) return null
        val bounds = outlineAt(text, REFERENCE_SIZE).bounds2D
        if (bounds.width <= 0.0 || bounds.height <= 0.0) return null
        return bounds.width / bounds.height
    }

    /**
     * The outline at [Ribbon.textSize], centred on the pivot and rotated, with every transform baked
     * into the coordinates. Null when there is nothing to draw.
     *
     * Baked rather than a `<group android:rotation>` because the monochrome output needs the ribbon
     * and the text in one `<path>` for even-odd fill. A group cannot straddle that.
     */
    fun fit(text: String, ribbon: Ribbon): FittedText? {
        if (text.isEmpty()) return null

        val referenceBounds = outlineAt(text, REFERENCE_SIZE).bounds2D
        if (referenceBounds.width <= 0.0 || referenceBounds.height <= 0.0) return null

        val scale = ribbon.textSize / referenceBounds.height
        if (!scale.isFinite() || scale <= 0.0) return null

        // Re-derive at the final size: hinting and advance rounding are size-dependent, so a scaled
        // reference outline is not what the font draws.
        val finalOutline = outlineAt(text, (REFERENCE_SIZE * scale).toFloat())
        val bounds = finalOutline.bounds2D
        if (bounds.width <= 0.0 || bounds.height <= 0.0) return null

        val transform = AffineTransform().apply {
            translate(ribbon.pivotX, ribbon.pivotY)
            // Positive degrees are clockwise here, matching SVG's rotate() under a y-down axis.
            rotate(Math.toRadians(ribbon.textRotationDegrees))
            translate(-bounds.centerX, -bounds.centerY)
        }
        return FittedText(
            pathData = finalOutline.toPathData(transform),
            glyphs = transform.createTransformedShape(finalOutline),
        )
    }

    private fun outlineAt(text: String, size: Float): Shape {
        val sized = font.deriveFont(size)
        val glyphs = sized.layoutGlyphVector(
            renderContext,
            text.toCharArray(),
            0,
            text.length,
            Font.LAYOUT_LEFT_TO_RIGHT,
        )
        return glyphs.outline
    }

    internal companion object {
        /**
         * Size the text is measured at before the fit scale is known. Large enough that rounding
         * inside the font engine does not distort the measurement, small enough to stay well away
         * from any size limits.
         */
        const val REFERENCE_SIZE: Float = 100f
    }
}

/** Walks a shape's path iterator into VectorDrawable `pathData`, preserving curve segments. */
internal fun Shape.toPathData(transform: AffineTransform?): String {
    val builder = StringBuilder()
    val segment = DoubleArray(6)
    val iterator = getPathIterator(transform)
    while (!iterator.isDone) {
        when (iterator.currentSegment(segment)) {
            PathIterator.SEG_MOVETO -> builder.command('M', segment, 2)
            PathIterator.SEG_LINETO -> builder.command('L', segment, 2)
            PathIterator.SEG_QUADTO -> builder.command('Q', segment, 4)
            PathIterator.SEG_CUBICTO -> builder.command('C', segment, 6)
            PathIterator.SEG_CLOSE -> {
                if (builder.isNotEmpty()) builder.append(' ')
                builder.append('Z')
            }
        }
        iterator.next()
    }
    return builder.toString()
}

private fun StringBuilder.command(command: Char, values: DoubleArray, count: Int) {
    if (isNotEmpty()) append(' ')
    append(command)
    for (index in 0 until count) {
        append(' ').append(values[index].toPathNumber())
    }
}
