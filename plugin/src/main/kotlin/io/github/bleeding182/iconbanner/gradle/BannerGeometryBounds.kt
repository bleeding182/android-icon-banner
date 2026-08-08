package io.github.bleeding182.iconbanner.gradle

import java.util.Locale

/**
 * Bounds check for `iconBanner.maxTextSize` and `iconBanner.lineHeight`, the two geometry knobs the
 * DSL exposes.
 *
 * The generator draws whatever it is given, and outside these ranges what it draws stops being a
 * ribbon without anything going wrong loudly enough for the user to notice.
 *
 * **Each value is checked on its own.** The two used to be validated as a pair — `maxTextSize`'s
 * ceiling was `30 / lineHeight` — on the reasoning that the *band* had to fit inside the mask's safe
 * zone, and the band is the text size times the line height. That coupling was wrong twice over. It
 * made the documented default of 13 illegal at a line height of 2.4, so the DSL could reject its own
 * default; and the premise does not hold. The band is centred on a fixed line, so extra thickness
 * grows symmetrically: the corner-side edge moves towards the corner, where a launcher's mask
 * declines to draw it, and the inner edge moves towards the middle of the icon, which is cosmetic.
 * Neither costs the text anything, because [io.github.bleeding182.iconbanner.generator.Ribbon] fits
 * the text against the chord across the safe zone and that chord does not depend on the thickness.
 * Only the text has to stay inside the safe zone, so only the text's size is bounded by it.
 *
 * The numbers here are plain, deliberately, so the geometry stays out of the Gradle layer.
 * `BannerGeometryBoundsTest` derives them from `Ribbon` instead, so retuning the geometry fails a
 * test rather than silently invalidating a bound.
 */
internal object BannerGeometryBounds {

    /** Text of zero or negative cap height is not text. */
    const val MIN_TEXT_SIZE: Int = 1

    /**
     * Largest cap height the text geometry holds for, as a percentage of the icon's shorter edge.
     *
     * The text is centred on the band's fixed centre line, which sits `0.198 * s` from the icon's
     * centre, and the safe zone's rim is at `0.306 * s` (66dp of 108). The glyphs reach half their
     * cap height either side of that line, so the corner-side half runs out of safe zone first: at
     * `2 * (0.306 - 0.198) = 21.5%` of the edge it is exactly on the rim, and past that a launcher's
     * mask starts cutting into the glyphs themselves. 21 is the last whole percent inside.
     *
     * Only the *text* is bounded here. The band around it may be as thick as `lineHeight` likes and
     * is allowed to spill past the rim, because the part outside the mask is simply not drawn and the
     * text neither moves nor shrinks when it happens.
     *
     * Nothing legitimate is refused: at 21 a two-letter marker is a third of the icon's edge tall.
     */
    const val MAX_TEXT_SIZE: Int = 21

    /**
     * Thinnest usable line. At exactly 1 the band is the height of the glyphs and there is no
     * clearance at all; below it the glyphs are taller than the band they sit in and hang out of
     * both long edges.
     */
    const val MIN_LINE_HEIGHT: Double = 1.0

    /**
     * Loosest usable line. Past this the band is mostly empty space with a small text in the middle
     * of it, which is the look the text-driven sizing exists to avoid.
     *
     * A sanity range rather than a geometric limit: nothing breaks at 3.1, it just stops looking like
     * a ribbon. 0 or a negative value, on the other hand, is a degenerate or inverted band, which is
     * the case this range has to catch.
     */
    const val MAX_LINE_HEIGHT: Double = 3.0

    /** Returns normally, or throws with a message naming [variantName] and the range. */
    fun check(maxTextSize: Int, lineHeight: Double, variantName: String) {
        require(lineHeight in MIN_LINE_HEIGHT..MAX_LINE_HEIGHT) {
            "iconBanner.lineHeight for variant '$variantName' is ${lineHeight.format()}, which is " +
                "outside the range ${MIN_LINE_HEIGHT.format()}..${MAX_LINE_HEIGHT.format()}. It is " +
                "the band's thickness as a multiple of the text's cap height; below " +
                "${MIN_LINE_HEIGHT.format()} the text is taller than the band around it. The default " +
                "is ${BannerDefaults.LINE_HEIGHT.format()}."
        }
        require(maxTextSize in MIN_TEXT_SIZE..MAX_TEXT_SIZE) {
            "iconBanner.maxTextSize for variant '$variantName' is $maxTextSize, which is outside the " +
                "range $MIN_TEXT_SIZE..$MAX_TEXT_SIZE. It is the text's cap height as a percentage of " +
                "the icon's edge length; past $MAX_TEXT_SIZE% the text no longer fits inside the " +
                "icon's safe zone and a launcher's mask starts cutting the glyphs off. The default is " +
                "${BannerDefaults.MAX_TEXT_SIZE}."
        }
    }

    /** Root locale, so the message reads the same on a comma-decimal JVM. */
    private fun Double.format(): String = String.format(Locale.ROOT, "%.1f", this).removeSuffix(".0")
}
