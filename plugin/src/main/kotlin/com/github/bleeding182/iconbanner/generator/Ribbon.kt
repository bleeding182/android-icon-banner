package com.github.bleeding182.iconbanner.generator

import com.github.bleeding182.iconbanner.api.BannerCorner

/**
 * The ribbon quad in the target vector's own viewport coordinates.
 *
 * Everything is derived from the viewport, so the same [com.github.bleeding182.iconbanner.api.BannerStyle]
 * produces the same visual result on a 108-unit adaptive icon foreground and on a 24-unit vector.
 */
internal class Ribbon(
    val viewportWidth: Double,
    val viewportHeight: Double,
    private val corner: BannerCorner,
    heightPercent: Double,
) {

    /** Shorter viewport edge; the unit everything normalises against. */
    private val s: Double = minOf(viewportWidth, viewportHeight)

    /** Distance from the corner to the ribbon's outer edge, along each axis. Not configurable. */
    val reach: Double = REACH_FRACTION * s

    /** Band width, measured perpendicular to the ribbon. */
    val bandWidth: Double = heightPercent / 100.0 * s

    val p1x: Double
    val p1y: Double
    val p2x: Double
    val p2y: Double
    val p3x: Double
    val p3y: Double
    val p4x: Double
    val p4y: Double

    /** Degrees, clockwise-positive — the VectorDrawable/SVG convention, y growing downward. */
    val textRotationDegrees: Double

    init {
        val b = reach
        val w = bandWidth
        val vw = viewportWidth
        val vh = viewportHeight
        when (corner) {
            BannerCorner.TOP_LEFT -> {
                p1x = b; p1y = 0.0
                p2x = 0.0; p2y = b
                p3x = 0.0; p3y = b - w
                p4x = b - w; p4y = 0.0
                textRotationDegrees = -45.0
            }

            BannerCorner.TOP_RIGHT -> {
                p1x = vw - b; p1y = 0.0
                p2x = vw; p2y = b
                p3x = vw; p3y = b - w
                p4x = vw - (b - w); p4y = 0.0
                textRotationDegrees = 45.0
            }

            BannerCorner.BOTTOM_LEFT -> {
                p1x = 0.0; p1y = vh - b
                p2x = b; p2y = vh
                p3x = b - w; p3y = vh
                p4x = 0.0; p4y = vh - (b - w)
                textRotationDegrees = 45.0
            }

            BannerCorner.BOTTOM_RIGHT -> {
                p1x = vw - b; p1y = vh
                p2x = vw; p2y = vh - b
                p3x = vw; p3y = vh - (b - w)
                p4x = vw - (b - w); p4y = vh
                textRotationDegrees = -45.0
            }
        }
    }

    /** Centre of the band; the text is centred here and rotated about it. */
    val pivotX: Double get() = (p1x + p2x + p3x + p4x) / 4.0
    val pivotY: Double get() = (p1y + p2y + p3y + p4y) / 4.0

    /** Space kept clear on every side of the text, as a fraction of the band width. */
    val padding: Double get() = TEXT_PADDING_FRACTION * bandWidth

    /** Text height budget: the band, less padding above and below. */
    val availableTextHeight: Double get() = bandWidth - 2 * padding

    /**
     * Text length budget: the band's centre line where it crosses the icon, less padding at each
     * end. The centre line sits at `reach - bandWidth/2` along both axes, and the chord of a 45°
     * band spanning that much on each axis is `sqrt(2)` times as long.
     */
    val availableTextLength: Double get() = (reach - bandWidth / 2.0) * SQRT_2 - 2 * padding

    /** `M p1 L p2 L p3 L p4 Z`. */
    fun quadPathData(): String = buildString {
        append("M ").append(p1x.toPathNumber()).append(' ').append(p1y.toPathNumber())
        append(" L ").append(p2x.toPathNumber()).append(' ').append(p2y.toPathNumber())
        append(" L ").append(p3x.toPathNumber()).append(' ').append(p3y.toPathNumber())
        append(" L ").append(p4x.toPathNumber()).append(' ').append(p4y.toPathNumber())
        append(" Z")
    }

    /**
     * Everything *except* the ribbon, as two subpaths: the small triangle cut off in the corner,
     * and the rest of the icon. Used as the monochrome `<clip-path>` so icon content cannot bleed
     * into the band.
     */
    fun inverseClipPathData(): String {
        val vw = viewportWidth
        val vh = viewportHeight
        val zero = 0.0
        return when (corner) {
            BannerCorner.TOP_LEFT -> listOf(
                poly(zero to zero, p4x to zero, zero to p3y),
                poly(p1x to zero, vw to zero, vw to vh, zero to vh, zero to p2y),
            )

            BannerCorner.TOP_RIGHT -> listOf(
                poly(vw to zero, p4x to zero, vw to p3y),
                poly(p1x to zero, zero to zero, zero to vh, vw to vh, vw to p2y),
            )

            // Note the axis swap against the other three corners: at the bottom-left the inner
            // edge runs from p3 (on the bottom edge) to p4 (on the left edge), where elsewhere it
            // runs from p4 to p3. Using the other corners' p4x/p3y here collapses the triangle to
            // a single point and silently drops the clip.
            BannerCorner.BOTTOM_LEFT -> listOf(
                poly(zero to vh, p3x to vh, zero to p4y),
                poly(zero to p1y, zero to zero, vw to zero, vw to vh, p2x to vh),
            )

            BannerCorner.BOTTOM_RIGHT -> listOf(
                poly(vw to vh, p4x to vh, vw to p3y),
                poly(p1x to vh, zero to vh, zero to zero, vw to zero, vw to p2y),
            )
        }.joinToString(" ")
    }

    private fun poly(vararg points: Pair<Double, Double>): String = buildString {
        points.forEachIndexed { index, (x, y) ->
            append(if (index == 0) "M " else " L ")
            append(x.toPathNumber()).append(' ').append(y.toPathNumber())
        }
        append(" Z")
    }

    internal companion object {
        /**
         * How far along each axis the ribbon's outer edge sits, as a fraction of the shorter
         * viewport edge. Carried over from the browser generator's default; deliberately fixed so
         * the DSL has one size knob rather than two.
         */
        const val REACH_FRACTION: Double = 0.75

        /**
         * Clearance around the text, as a fraction of the band width. Tuned by eye at launcher
         * size: below roughly 0.12 the glyphs touch the band edges, above roughly 0.25 short text
         * looks lost. Named so it can be moved without hunting through the geometry.
         */
        const val TEXT_PADDING_FRACTION: Double = 0.18

        private val SQRT_2: Double = Math.sqrt(2.0)
    }
}
