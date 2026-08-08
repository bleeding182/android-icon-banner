package com.github.bleeding182.iconbanner.generator

import java.awt.Font
import java.awt.Shape
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.awt.geom.PathIterator
import java.io.File
import java.util.Locale

/**
 * Turns text into VectorDrawable `pathData`, using the JDK's own font support.
 *
 * Two properties of `GlyphVector.getOutline()` make this work without a font library, both
 * confirmed by prototype: quadratic and cubic segments survive rather than being flattened into
 * line soup, and the coordinate convention is baseline-at-zero with y increasing downward — exactly
 * the VectorDrawable convention. No axis flip, no correction transform.
 */
internal class BannerText(fontFile: File) {

    /**
     * Antialiasing and fractional metrics on. Fractional metrics in particular keeps advances off
     * the integer grid, so glyph positions scale linearly with size instead of snapping.
     */
    private val renderContext = FontRenderContext(null, true, true)

    val font: Font = Font.createFont(Font.TRUETYPE_FONT, fontFile)

    /**
     * The first character [font] cannot draw, or null when it can draw all of [text].
     *
     * A row of missing-glyph boxes stamped onto the launcher icon is worse than a build error, so
     * the caller turns this into a failure.
     */
    fun firstUndisplayableCharacter(text: String): String? {
        val index = font.canDisplayUpTo(text)
        if (index < 0) return null
        val codePoint = text.codePointAt(index)
        val rendered = String(Character.toChars(codePoint))
        return String.format(Locale.ROOT, "'%s' (U+%04X)", rendered, codePoint)
    }

    /**
     * The text outline, auto-fitted to the band, centred on the pivot and rotated into place, with
     * every transform baked into the coordinates.
     *
     * The transform is baked rather than emitted as a `<group android:rotation="...">` because the
     * monochrome output needs the ribbon and the text to share one `<path>` element so even-odd
     * fill can punch the glyphs out as holes. A group cannot straddle that.
     *
     * Returns null when there is nothing to draw — empty text, or text that is all whitespace and
     * therefore has an empty outline.
     */
    fun outlinePathData(text: String, ribbon: Ribbon): String? {
        if (text.isEmpty()) return null

        val reference = outlineAt(text, REFERENCE_SIZE)
        val referenceBounds = reference.bounds2D
        if (referenceBounds.width <= 0.0 || referenceBounds.height <= 0.0) return null

        val heightScale = ribbon.availableTextHeight / referenceBounds.height
        val lengthScale = ribbon.availableTextLength / referenceBounds.width
        val scale = minOf(heightScale, lengthScale)
        if (!scale.isFinite() || scale <= 0.0) return null

        // Re-derive at the final size rather than scaling the reference outline: hinting and
        // advance rounding are size-dependent, so the glyphs the font actually draws at this size
        // are not exactly the reference ones scaled.
        val finalOutline = outlineAt(text, (REFERENCE_SIZE * scale).toFloat())
        val bounds = finalOutline.bounds2D
        if (bounds.width <= 0.0 || bounds.height <= 0.0) return null

        val transform = AffineTransform().apply {
            translate(ribbon.pivotX, ribbon.pivotY)
            // Positive degrees are clockwise here, matching SVG's rotate() under a y-down axis.
            rotate(Math.toRadians(ribbon.textRotationDegrees))
            translate(-bounds.centerX, -bounds.centerY)
        }
        return finalOutline.toPathData(transform)
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
