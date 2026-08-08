package io.github.bleeding182.iconbanner.gradle

import java.util.Locale

/**
 * Bounds check for `iconBanner.maxTextSize` and `iconBanner.lineHeight`, the two geometry knobs the
 * DSL exposes.
 *
 * The generator draws whatever it is given, and outside these ranges what it draws stops being a
 * ribbon without anything going wrong loudly enough for the user to notice.
 *
 * The numbers here are plain, deliberately, so the geometry stays out of the Gradle layer.
 * `BannerTextSizeTest` derives them from `Ribbon` instead, so retuning the geometry fails a test
 * rather than silently invalidating a bound.
 */
internal object BannerTextSize {

    /** Text of zero or negative cap height is not text. */
    const val MIN: Int = 1

    /**
     * Widest band the ribbon geometry still holds for, as a percentage of the icon's shorter edge.
     *
     * The band is centred on a fixed line — `Ribbon.CENTRE_LINE_FRACTION` — so it grows outwards in
     * both directions, and it is the corner-side edge that runs out of icon first. At 30% that edge
     * sits `0.304 * s` from the icon's centre, just inside the 66dp safe zone (`0.306 * s`); one
     * percentage point more and it is outside, at which point a launcher's mask starts cutting into
     * the band's thickness and the band renders thinner than asked for, with no error at all.
     *
     * Nothing legitimate is refused: a band 30% of the icon's edge already covers a third of the
     * visible icon.
     */
    const val MAX_BAND_PERCENT: Double = 30.0

    /**
     * Thinnest usable line. At exactly 1 the band is the height of the glyphs and there is no
     * clearance at all; below it the glyphs are taller than the band they sit in and hang out of
     * both long edges.
     */
    const val MIN_LINE_HEIGHT: Double = 1.0

    /**
     * Loosest usable line. Past this the band is mostly empty space with a small text in the middle
     * of it, which is the look the text-driven sizing exists to avoid, and the band width bound
     * leaves so little room for the text that the marking is unreadable anyway.
     */
    const val MAX_LINE_HEIGHT: Double = 3.0

    /**
     * Largest `maxTextSize` that still fits [MAX_BAND_PERCENT] at this line height.
     *
     * The ceiling moves because it is the *band* the geometry constrains, and the band is
     * `maxTextSize * lineHeight`. At the default line height it is 20.
     */
    fun maxFor(lineHeight: Double): Int = (MAX_BAND_PERCENT / lineHeight).toInt()

    /** Returns normally, or throws with a message naming [variantName] and the range. */
    fun check(maxTextSize: Int, lineHeight: Double, variantName: String) {
        require(lineHeight in MIN_LINE_HEIGHT..MAX_LINE_HEIGHT) {
            "iconBanner.lineHeight for variant '$variantName' is ${lineHeight.format()}, which is " +
                "outside the range ${MIN_LINE_HEIGHT.format()}..${MAX_LINE_HEIGHT.format()}. It is " +
                "the band's width as a multiple of the text's cap height; below " +
                "${MIN_LINE_HEIGHT.format()} the text is taller than the band around it. The default " +
                "is ${BannerDefaults.LINE_HEIGHT.format()}."
        }
        val max = maxFor(lineHeight)
        require(maxTextSize in MIN..max) {
            "iconBanner.maxTextSize for variant '$variantName' is $maxTextSize, which is outside the " +
                "range $MIN..$max at a lineHeight of ${lineHeight.format()}. It is the text's cap " +
                "height as a percentage of the icon's edge length, and the band around it is " +
                "maxTextSize times lineHeight; past ${MAX_BAND_PERCENT.format()}% of the edge the " +
                "band no longer fits inside the icon's safe zone. The default is " +
                "${BannerDefaults.MAX_TEXT_SIZE}."
        }
    }

    /** Root locale, so the message reads the same on a comma-decimal JVM. */
    private fun Double.format(): String = String.format(Locale.ROOT, "%.1f", this).removeSuffix(".0")
}
