package io.github.bleeding182.iconbanner.gradle

import java.util.Locale

/**
 * Bounds for the numeric knobs. Outside them the generator still draws something, but it stops
 * being a ribbon without failing loudly.
 *
 * Each value is checked on its own: of the three geometric ones only the text has to stay inside
 * the safe zone, and neither `lineHeight` nor `position` changes the size it is fitted to.
 * `BannerBoundsTest` derives those from `Ribbon`, so retuning the geometry fails a test.
 */
internal object BannerBounds {

    /** Text of zero or negative cap height is not text. */
    const val MIN_TEXT_SIZE: Int = 1

    /**
     * Largest cap height whose corner-side half is still inside the safe zone **at position 65**.
     *
     * Static, although the true ceiling tightens as the band moves out — to 6% at 90. Validating
     * against that would make the default of 13 illegal past about 78; `Ribbon` clamps instead.
     */
    const val MAX_TEXT_SIZE: Int = 21

    /** At 1 the band is exactly the height of the glyphs; below it they hang out of both edges. */
    const val MIN_LINE_HEIGHT: Double = 1.0

    /** Taste, not geometry. What the range must catch is 0 or negative, an inverted band. */
    const val MAX_LINE_HEIGHT: Double = 3.0

    /**
     * Cosmetic: the text only gains room towards the centre, but by 20 the ribbon is a stripe
     * through the artwork with its own corner left bare.
     */
    const val MIN_POSITION: Int = 20

    /**
     * Geometric: the text budget reaches zero at 100, and 95 already leaves five characters
     * under 3dp.
     */
    const val MAX_POSITION: Int = 95

    /** A percentage of a byte, so the whole range is usable. 0 leaves the band as bare cutout. */
    const val MIN_MONOCHROME_ALPHA: Int = 0
    const val MAX_MONOCHROME_ALPHA: Int = 100

    /**
     * Throws naming the range, the variant and the banner — a variant merges each banner from a
     * different set of blocks.
     */
    fun check(
        maxTextSize: Int,
        lineHeight: Double,
        position: Int,
        monochromeAlpha: Int,
        variantName: String,
        bannerName: String,
    ) {
        require(monochromeAlpha in MIN_MONOCHROME_ALPHA..MAX_MONOCHROME_ALPHA) {
            "iconBanner.monochromeAlpha for banner '$bannerName' in variant '$variantName' is " +
                "$monochromeAlpha, which is outside the range " +
                "$MIN_MONOCHROME_ALPHA..$MAX_MONOCHROME_ALPHA. It is how opaque the band is in the " +
                "themed icon, as a percentage. The default is ${BannerDefaults.MONOCHROME_ALPHA}."
        }
        require(position in MIN_POSITION..MAX_POSITION) {
            "iconBanner.position for banner '$bannerName' in variant '$variantName' is $position, " +
                "which is outside the range $MIN_POSITION..$MAX_POSITION. It is how far out the " +
                "ribbon sits, as a percentage: 0 is the centre of the icon and 100 the point at " +
                "which no text fits at all. The default is ${BannerDefaults.POSITION}."
        }
        require(lineHeight in MIN_LINE_HEIGHT..MAX_LINE_HEIGHT) {
            "iconBanner.lineHeight for banner '$bannerName' in variant '$variantName' is " +
                "${lineHeight.format()}, which is outside the range " +
                "${MIN_LINE_HEIGHT.format()}..${MAX_LINE_HEIGHT.format()}. It is the band's thickness " +
                "as a multiple of the text's cap height; below ${MIN_LINE_HEIGHT.format()} the text " +
                "is taller than the band around it. The default is " +
                "${BannerDefaults.LINE_HEIGHT.format()}."
        }
        require(maxTextSize in MIN_TEXT_SIZE..MAX_TEXT_SIZE) {
            "iconBanner.maxTextSize for banner '$bannerName' in variant '$variantName' is " +
                "$maxTextSize, which is outside the range $MIN_TEXT_SIZE..$MAX_TEXT_SIZE. It is the " +
                "text's cap height as a percentage of the icon's edge length; past $MAX_TEXT_SIZE% " +
                "the text no longer fits inside the icon's safe zone and a launcher's mask starts " +
                "cutting the glyphs off. The default is ${BannerDefaults.MAX_TEXT_SIZE}."
        }
    }

    /** Root locale, so the message reads the same on a comma-decimal JVM. */
    private fun Double.format(): String = String.format(Locale.ROOT, "%.1f", this).removeSuffix(".0")
}
