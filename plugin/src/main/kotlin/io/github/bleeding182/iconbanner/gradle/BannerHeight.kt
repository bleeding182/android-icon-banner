package io.github.bleeding182.iconbanner.gradle

/**
 * Bounds check for `iconBanner.height`, the one geometry knob the DSL exposes.
 *
 * The generator draws whatever it is given, and outside this range what it draws stops being a ribbon
 * without anything going wrong loudly enough for the user to notice.
 */
internal object BannerHeight {

    /** A band of zero or negative width is a degenerate quad, not a ribbon. */
    const val MIN: Int = 1

    /**
     * Widest band the ribbon geometry still holds for, as a percentage of the icon's edge.
     *
     * `Ribbon.reach` is `CORNER_EDGE_FRACTION * s + bandWidth`, clamped to `s`. With that fraction at
     * 0.60 the clamp is reached exactly at 40 (`0.60 * s + 0.40 * s == s`), and above it the
     * corner-side edge the whole design anchors on — documented at `Ribbon.CORNER_EDGE_FRACTION`, so
     * that a taller band grows *inwards* rather than off the masked area — quietly starts sliding
     * back out towards the corner instead. `RibbonTest` pins that this bound still matches the
     * geometry.
     *
     * Beyond that it degrades rather than merely inverting: past 100 the band is wider than the icon,
     * so the quad's inner edge has negative coordinates, and `Ribbon.availableTextLength` goes
     * negative, at which point the text vanishes altogether and all the user sees is a coloured wedge
     * over the icon — with no error at all.
     *
     * 40 is already far past anything usable, since the band then covers more than a third of the
     * icon, so nothing legitimate is refused here.
     */
    const val MAX: Int = 40

    /** Returns [value] unchanged, or throws with a message naming [variantName] and the range. */
    fun check(value: Int, variantName: String): Int {
        require(value in MIN..MAX) {
            "iconBanner.height for variant '$variantName' is $value, which is outside the range " +
                "$MIN..$MAX. It is the ribbon's width as a percentage of the icon's edge length; " +
                "above $MAX the ribbon no longer fits the corner it is anchored to. The default is " +
                "${BannerDefaults.HEIGHT}."
        }
        return value
    }
}
