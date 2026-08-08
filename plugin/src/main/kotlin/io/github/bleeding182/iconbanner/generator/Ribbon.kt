package io.github.bleeding182.iconbanner.generator

import io.github.bleeding182.iconbanner.api.BannerCorner

/**
 * The ribbon quad, in the target vector's own viewport units.
 *
 * Names ending in `Axis` are intercepts along the x and y axes and are what the quad is built from;
 * everything else is a true distance measured across or along the ribbon. The two differ by √2, and
 * confusing them is silent.
 *
 * Only *opposite* corners are disjoint: two bands cross `0.28s` from the icon's centre, inside the
 * mask. Two banners in one corner overlap at any `position`, both being parallel to one diagonal.
 */
internal class Ribbon(
    val viewportWidth: Double,
    val viewportHeight: Double,
    private val corner: BannerCorner,
    positionPercent: Double,
    maxTextSizePercent: Double,
    lineHeight: Double,
    textWidthPerCapHeight: Double? = null,
) {

    val s: Double = minOf(viewportWidth, viewportHeight)

    private val safeRadius: Double = SAFE_ZONE_FRACTION * s

    /** Perpendicular distance from the icon's centre to the band's centre line. Primary. */
    val centreLineFromCentre: Double = positionPercent / 100.0 * safeRadius

    val centreLineAxis: Double = s - centreLineFromCentre * SQRT_2

    /** The chord across the **safe zone**: text sized to the square is sheared off at the mask. */
    val textLengthBudget: Double = run {
        val halfChord = safeRadius * safeRadius - centreLineFromCentre * centreLineFromCentre
        if (halfChord > 0) 2 * Math.sqrt(halfChord) else 0.0
    }

    /**
     * As asked for, as long as the ribbon allows, or as tall as the safe zone allows at this
     * position — whichever is smallest. `lineHeight` is deliberately absent: thickness must never
     * decide whether the text fits.
     */
    val textSize: Double = run {
        val requested = maxTextSizePercent / 100.0 * s
        val safeZoneAllows = 2 * (safeRadius - centreLineFromCentre)
        val lengthAllows = textWidthPerCapHeight
            ?.let { textLengthBudget / (it + 2 * END_PADDING_PER_CAP_HEIGHT) }
            ?: Double.MAX_VALUE
        minOf(requested, lengthAllows, safeZoneAllows)
    }.coerceAtLeast(0.0)

    /** Across the ribbon — what `lineHeight` names. */
    val bandThickness: Double = textSize * lineHeight

    /** √2 larger: moving a 45° edge `t` perpendicular moves its intercept by `t * √2`. */
    val bandWidthAxis: Double get() = bandThickness * SQRT_2

    val innerEdgeAxis: Double = centreLineAxis + bandWidthAxis / 2.0

    val cornerSideEdgeAxis: Double get() = innerEdgeAxis - bandWidthAxis

    val p1x: Double
    val p1y: Double
    val p2x: Double
    val p2y: Double
    val p3x: Double
    val p3y: Double
    val p4x: Double
    val p4y: Double

    /** Clockwise-positive, y growing downward. */
    val textRotationDegrees: Double

    init {
        // p1/p2 on the inner edge, p3/p4 on the corner-side edge.
        val inner = innerEdgeAxis
        val outer = cornerSideEdgeAxis
        val vw = viewportWidth
        val vh = viewportHeight
        when (corner) {
            BannerCorner.TOP_LEFT -> {
                p1x = inner; p1y = 0.0
                p2x = 0.0; p2y = inner
                p3x = 0.0; p3y = outer
                p4x = outer; p4y = 0.0
                textRotationDegrees = -45.0
            }

            BannerCorner.TOP_RIGHT -> {
                p1x = vw - inner; p1y = 0.0
                p2x = vw; p2y = inner
                p3x = vw; p3y = outer
                p4x = vw - outer; p4y = 0.0
                textRotationDegrees = 45.0
            }

            BannerCorner.BOTTOM_LEFT -> {
                p1x = 0.0; p1y = vh - inner
                p2x = inner; p2y = vh
                p3x = outer; p3y = vh
                p4x = 0.0; p4y = vh - outer
                textRotationDegrees = 45.0
            }

            BannerCorner.BOTTOM_RIGHT -> {
                p1x = vw - inner; p1y = vh
                p2x = vw; p2y = vh - inner
                p3x = vw; p3y = vh - outer
                p4x = vw - outer; p4y = vh
                textRotationDegrees = -45.0
            }
        }
    }

    /** Centre of the band; the text is centred here and rotated about it. */
    val pivotX: Double get() = (p1x + p2x + p3x + p4x) / 4.0
    val pivotY: Double get() = (p1y + p2y + p3y + p4y) / 4.0

    val padding: Double get() = (bandThickness - textSize) / 2.0

    val endPadding: Double get() = END_PADDING_PER_CAP_HEIGHT * textSize

    /** Inverse of how [centreLineAxis] is derived. Larger means closer to the rim. */
    fun perpendicularFromIconCentre(axisDistance: Double): Double = (s - axisDistance) / SQRT_2

    fun quadPathData(): String = buildString {
        append("M ").append(p1x.toPathNumber()).append(' ').append(p1y.toPathNumber())
        append(" L ").append(p2x.toPathNumber()).append(' ').append(p2y.toPathNumber())
        append(" L ").append(p3x.toPathNumber()).append(' ').append(p3y.toPathNumber())
        append(" L ").append(p4x.toPathNumber()).append(' ').append(p4y.toPathNumber())
        append(" Z")
    }

    /**
     * Everything except the ribbon: the corner triangle, then the rest of the icon. The monochrome
     * `<clip-path>`. Built from the quad's own points, so the two cannot drift apart.
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

            // Axis swap: here the inner edge runs p3 to p4, not p4 to p3. Using the other corners'
            // p4x/p3y collapses the triangle to a point.
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
        /** 0 is the icon's centre, 100 the distance at which [textLengthBudget] reaches zero. */
        const val DEFAULT_POSITION_PERCENT: Double = 65.0

        const val END_PADDING_PER_CAP_HEIGHT: Double = 0.30

        /** Safe zone, 66dp of 108. The mask is 72dp; the margin covers mask *shape*. */
        const val SAFE_ZONE_FRACTION: Double = 33.0 / 108.0

        /** Only [bandWidthAxis], [centreLineAxis] and [perpendicularFromIconCentre] may use this. */
        private val SQRT_2: Double = Math.sqrt(2.0)
    }
}
