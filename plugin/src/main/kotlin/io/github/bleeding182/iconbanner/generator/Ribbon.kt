package io.github.bleeding182.iconbanner.generator

import io.github.bleeding182.iconbanner.api.BannerCorner

/**
 * The ribbon quad in the target vector's own viewport coordinates.
 *
 * Everything is derived from the viewport, so the same [io.github.bleeding182.iconbanner.api.BannerStyle]
 * produces the same visual result on a 108-unit adaptive icon foreground and on a 24-unit vector.
 *
 * The band is sized **from the text**, not the other way round: [textSize] is the cap height the
 * text ends up at, and [bandThickness] is that times the line height, so the band hugs whatever the
 * text turned out to be. The band's *position* is fixed and independent of its thickness — see
 * [CENTRE_LINE_FRACTION] for why that separation is load-bearing.
 *
 * ### Two kinds of length, differing by √2
 *
 * The ribbon runs at 45°, so a length here is one of two incompatible things:
 *
 * - Names ending in `Axis` are measured **along the x and y axes** from the corner. They are line
 *   intercepts, and they are what the quad's coordinates are literally built from.
 * - Everything else is a **true distance**, measured where the geometry means it: [bandThickness]
 *   and [padding] across the ribbon, [textLengthBudget] and [endPadding] along it, [textSize] as an
 *   ink height across the ribbon.
 *
 * Confusing the two is not a rounding error and it is not hypothetical: [bandThickness] used to be
 * written straight into the quad's axis offsets, which made every band `1/√2` — 71% — of the
 * thickness `lineHeight` asked for, with the glyphs left visually touching both edges. The naming
 * carries the distinction so that cannot be written again, and [perpendicularFromIconCentre] is the
 * one place an axis measurement is converted, rather than a bare `SQRT_2` at each point of use.
 *
 * @param maxTextSizePercent cap height asked for, as a percentage of the shorter viewport edge. An
 *   upper bound rather than a size: text too long for the band shrinks below it.
 * @param lineHeight the band's thickness as a multiple of [textSize], measured across the ribbon. At
 *   1 the band is exactly the height of the glyphs; the surplus becomes [padding] above and below
 *   them.
 * @param textWidthPerCapHeight the text's natural width per unit of cap height, from
 *   [BannerText.naturalWidthPerCapHeight]. Null when there is no text to fit, in which case the band
 *   is sized for [maxTextSizePercent] as though the text were short enough to reach it.
 */
internal class Ribbon(
    val viewportWidth: Double,
    val viewportHeight: Double,
    private val corner: BannerCorner,
    maxTextSizePercent: Double,
    lineHeight: Double,
    textWidthPerCapHeight: Double? = null,
) {

    /** Shorter viewport edge; the unit everything normalises against. */
    val s: Double = minOf(viewportWidth, viewportHeight)

    /**
     * Where the band's centre line sits, measured along each axis from the corner. Fixed — see
     * [CENTRE_LINE_FRACTION].
     */
    val centreLineAxis: Double = CENTRE_LINE_FRACTION * s

    /**
     * How much length the text has along the centre line, before the clearance at each end.
     *
     * A true distance, measured along the ribbon, and built from two others: the safe zone's radius
     * and the perpendicular distance from the icon's centre to the centre line. No axis measurement
     * survives into it, which is why there is no √2 anywhere in the expression.
     *
     * The chord across the icon's **safe zone**, not across the square: a launcher masks an adaptive
     * icon to a circle, so text sized to the square runs out past the rim and is sheared off —
     * which is exactly what happened before this was here. A banner nobody can read defeats the
     * point of the plugin, so long text shrinks rather than spills.
     *
     * Only the safe zone appears here, where there used to also be a chord across the square. With
     * the centre line fixed at [CENTRE_LINE_FRACTION] the safe circle is always the tighter of the
     * two by a wide margin — 0.47 of the edge against 1.02 — so the square limit could never bind.
     *
     * Independent of [bandThickness], which is the whole point: it is what lets the band hug the
     * text without the hug feeding back into how much room the text has.
     */
    val textLengthBudget: Double = run {
        val safeRadius = SAFE_ZONE_FRACTION * s
        val centreLineOffset = perpendicularFromIconCentre(centreLineAxis)
        val halfChord = safeRadius * safeRadius - centreLineOffset * centreLineOffset
        if (halfChord > 0) 2 * Math.sqrt(halfChord) else 0.0
    }

    /**
     * Cap height the text is drawn at: what was asked for, or as much as the band's length allows.
     *
     * Closed form, not a search. [textLengthBudget] no longer depends on the band thickness, and the
     * clearance at the ends scales with the text, so "the text plus its end clearance fills the
     * chord" is linear in the size:
     *
     * ```
     * width * size + 2 * END_PADDING_PER_CAP_HEIGHT * size = textLengthBudget
     * ```
     *
     * Note what is *not* in it: `lineHeight`. Thickness is cosmetic and must never decide whether
     * the text fits, so the fitted size is byte-identical across line heights.
     */
    val textSize: Double = run {
        val requested = maxTextSizePercent / 100.0 * s
        if (textWidthPerCapHeight == null) {
            requested
        } else {
            val lengthAllows = textLengthBudget / (textWidthPerCapHeight + 2 * END_PADDING_PER_CAP_HEIGHT)
            minOf(requested, lengthAllows)
        }
    }.coerceAtLeast(0.0)

    /**
     * The band's thickness, measured perpendicular to the ribbon — the distance between its two long
     * edges, and the number `lineHeight` names.
     *
     * Hugs [textSize] by construction, and the two are directly comparable because a cap height is
     * measured across the ribbon too: at a line height of 1 the band is exactly as thick as the
     * glyphs are tall.
     */
    val bandThickness: Double = textSize * lineHeight

    /**
     * The same thickness as the offset between the band's two long edges **along each axis**, which
     * is what the quad's coordinates are built from.
     *
     * √2 larger than [bandThickness], and that factor is geometry rather than fudge: a top-left
     * band's long edges lie on `x + y = c`, so moving one of them `t` perpendicular moves its axis
     * intercept by `t * √2`. Writing [bandThickness] into the quad directly is what made every band
     * 29% thinner than asked for.
     */
    val bandWidthAxis: Double get() = bandThickness * SQRT_2

    /**
     * Distance from the corner to the ribbon's inner edge — the one deeper into the icon — along
     * each axis.
     *
     * Derived from [centreLineAxis], so the band grows symmetrically about a fixed centre line: a
     * thicker band no longer moves the ribbon, and a thinner one no longer drags it towards the
     * corner where there is less room for text.
     */
    val innerEdgeAxis: Double = centreLineAxis + bandWidthAxis / 2.0

    /** Distance from the corner to the ribbon's corner-side edge, along each axis. */
    val cornerSideEdgeAxis: Double get() = innerEdgeAxis - bandWidthAxis

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
        // Both axis measurements: p1/p2 sit on the inner edge, p3/p4 on the corner-side edge.
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

    /**
     * Centre of the band; the text is centred here and rotated about it.
     *
     * Sits on the centre line whatever the thickness is — the two edges move apart symmetrically, so
     * the pivot is `centreLineAxis / 2` on both axes and `lineHeight` cannot shift the text.
     */
    val pivotX: Double get() = (p1x + p2x + p3x + p4x) / 4.0
    val pivotY: Double get() = (p1y + p2y + p3y + p4y) / 4.0

    /**
     * Space between the text and each of the band's long edges, measured across the ribbon. Not a
     * knob of its own: it is whatever [bandThickness] left over once the glyphs are in, so asking
     * for a looser line loosens the band around the text.
     */
    val padding: Double get() = (bandThickness - textSize) / 2.0

    /**
     * Clearance kept at each end of the text, inside [textLengthBudget].
     *
     * Measured along the ribbon, the same direction as [textLengthBudget] and the text's own advance,
     * so no axis conversion is involved on either side of that comparison.
     */
    val endPadding: Double get() = END_PADDING_PER_CAP_HEIGHT * textSize

    /**
     * Perpendicular distance from the icon's centre to a line at [axisDistance] from the corner —
     * the one crossing between the two kinds of length in this class.
     *
     * A top-left line at axis intercept `a` is `x + y = a`, and the point-line distance from
     * `(s/2, s/2)` to it is `(s - a) / √2`. Positive towards the corner, so a larger result means
     * closer to the rim; compare it against [SAFE_ZONE_FRACTION] times [s] to ask whether a mask
     * will cut the line off.
     */
    fun perpendicularFromIconCentre(axisDistance: Double): Double = (s - axisDistance) / SQRT_2

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
     *
     * Built from the quad's own points rather than from [innerEdgeAxis] and [cornerSideEdgeAxis]
     * again, so the clip is the exact complement of the band that was drawn and cannot drift from it.
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
         * Where the band's centre line sits, as a fraction of the shorter viewport edge measured
         * along each axis from the corner. Deliberately fixed, and deliberately independent of the
         * band thickness.
         *
         * That independence is the whole point. The band used to be anchored on its corner-side
         * edge, which made the band thickness buy ribbon position: a thicker band grew inwards, the
         * centre line moved inwards with it, and the chord across the safe zone — the text's length
         * budget — grew. That was harmless while the band thickness was a user setting, but it is
         * fatal once the band is derived from the text: the text would shrink the band, the thinner
         * band would drag the ribbon back towards the corner, the shorter chord there would shrink
         * the text again, and the two would spiral down together. Solving that equilibrium for
         * `"STAGING"` in Black Ops One gives a 4.45-unit cap height — under 3dp on a launcher, worse
         * than the model this replaced. With the centre line pinned, the same text solves to 6.5.
         *
         * 0.72 is set against the **adaptive-icon mask** rather than by eye on a full 108 square,
         * and the trade it settles is text room against how much of the icon the band covers.
         *
         * The lower bound is legibility. Moving the centre line outwards shortens the chord across
         * the safe zone quickly — 0.47 of the edge here, 0.39 at 0.67 — and the far end of that is
         * already unusable: `"STAGING"` in the sample app's display face solves to 3.6dp there,
         * under the 4dp floor. At 0.72 it gets 4.3dp. Note that this is *not* the point where the
         * corner-side edge sits on the safe-zone rim, which is where the old corner-side anchor was
         * pinned; that point is 0.67, and it fails on text size.
         *
         * The upper bound is the icon's middle. At the default style the inner edge sits `0.100 * s`
         * from the centre and the corner-side edge `0.295 * s`, still inside the 66dp safe zone
         * (`0.306 * s`), so the default band's full thickness survives every launcher mask with the
         * centre of the artwork still visible. Pushing further in keeps buying text room (0.54 of the
         * edge at 0.80) but covers more of the icon: at 0.80 the inner edge is `0.044 * s` from the
         * centre and the band reads as a stripe across the artwork rather than a corner ribbon.
         *
         * Those two edge figures are properties of the default `lineHeight`, not constraints on it.
         * A thicker band is allowed to push its corner-side edge past the rim — the mask simply does
         * not draw that part — because only the text has to stay inside the safe zone, and the text's
         * size does not depend on the thickness. See `BannerGeometryBounds`.
         */
        const val CENTRE_LINE_FRACTION: Double = 0.72

        /**
         * Clearance kept at each end of the text, as a fraction of the text's own cap height.
         *
         * About the width of a word space at the same size: enough that the outermost glyph reads as
         * sitting inside the band rather than against the point where the mask cuts it off, and
         * enough to cover the corners of the text's box poking a little past its centre line. It
         * also absorbs the small difference between the glyphs measured at the reference size and
         * the ones the font actually draws at the fitted size.
         *
         * Per *cap height*, not per band thickness, which is what the old model used. A fraction of
         * the thickness meant a thicker band ate the chord from both ends for no reason — at the old
         * maximum it removed 27% of the available length — and it made the clearance grow while the
         * text it was supposed to protect stayed the same size.
         */
        const val END_PADDING_PER_CAP_HEIGHT: Double = 0.30

        /**
         * Radius of the adaptive-icon safe zone — 66dp of the 108dp canvas — as a fraction of the
         * shorter viewport edge. Content inside it is guaranteed to survive every launcher mask.
         * The mask itself is 72dp, but that extra margin covers mask *shape*, and a circle is the
         * worst case for something running diagonally across a corner.
         */
        const val SAFE_ZONE_FRACTION: Double = 33.0 / 108.0

        /** Only [bandWidthAxis] and [perpendicularFromIconCentre] may use this. */
        private val SQRT_2: Double = Math.sqrt(2.0)
    }
}
