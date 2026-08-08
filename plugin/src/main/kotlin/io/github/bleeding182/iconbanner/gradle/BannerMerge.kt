package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.api.BannerCorner
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

/** Values used when no block in the precedence chain sets one. */
internal object BannerDefaults {
    const val COLOR = "#FF0000"
    const val TEXT_COLOR = "#FFFFFF"
    val CORNER = BannerCorner.TOP_LEFT

    /**
     * Cap height as a percentage of the icon's shorter edge, and the band's thickness as a multiple
     * of it.
     *
     * 13 stays where it was: short text comes out 14.04 units of 108 — 9.4dp on a launcher — and
     * anything longer than about three characters is cut down by the length budget regardless, so the
     * value only decides how `QA` and `DEV` look.
     *
     * 1.5 is *not* continuity with the band-width knob these replaced, which is how it was first
     * arrived at. That reasoning was measuring the band along the axes, so a "line height" of 1.5
     * drew 1.06 cap heights of band and left the glyphs touching both edges. Read honestly it leaves
     * a quarter of the cap height clear above and below the text, which is where the ribbon stops
     * looking crowded; and at 13 the band's corner-side edge still lands inside the 66dp safe zone
     * (`0.295 * s` against `0.306 * s`), so even a short marker's band is drawn in full under every
     * launcher mask. Looser than this starts losing the corner of the band to the mask and pushing
     * the inner edge across the middle of the artwork.
     */
    const val MAX_TEXT_SIZE = 13
    const val LINE_HEIGHT = 1.5

    const val FONT = "Roboto Mono"
    const val WEIGHT = 700
    const val ITALIC = false
}

/**
 * A merged banner configuration for one variant. Every value is still lazy; nothing here has been
 * evaluated. A variant with no banner has no [ResolvedBanner] at all.
 */
internal class ResolvedBanner(
    val text: Provider<String>,
    val color: Provider<String>,
    val textColor: Provider<String>,
    val corner: Provider<BannerCorner>,
    val maxTextSize: Provider<Int>,
    val lineHeight: Provider<Double>,
    val fontFamily: Provider<String>,
    val fontWeight: Provider<Int>,
    val fontItalic: Provider<Boolean>,
)

/**
 * Merges the blocks that apply to one variant.
 *
 * [sources] must already be in precedence order — build type first, then product flavors in
 * dimension order, then the project-level block last. That is AGP's own rule for every other
 * setting, so users do not have to learn a second one.
 *
 * Returns `null` when the variant gets no banner: either nothing assigned `text` anywhere, or the
 * highest-precedence block that mentioned it assigned `null`.
 */
internal fun mergeBanner(sources: List<IconBannerDsl>): ResolvedBanner? {
    val text = resolveText(sources) ?: return null
    return ResolvedBanner(
        text = text,
        color = merge(sources, BannerDefaults.COLOR) { it.color },
        textColor = merge(sources, BannerDefaults.TEXT_COLOR) { it.textColor },
        corner = merge(sources, BannerDefaults.CORNER) { it.corner },
        maxTextSize = merge(sources, BannerDefaults.MAX_TEXT_SIZE) { it.maxTextSize },
        lineHeight = merge(sources, BannerDefaults.LINE_HEIGHT) { it.lineHeight },
        fontFamily = merge(sources, BannerDefaults.FONT) { it.font },
        fontWeight = merge(sources, BannerDefaults.WEIGHT) { it.weight },
        fontItalic = merge(sources, BannerDefaults.ITALIC) { it.italic },
    )
}

/**
 * The winning `text`, or `null` for "no banner". Decided purely from assignment state, so a
 * provider-valued text enables the banner without anyone having to know its value yet.
 */
private fun resolveText(sources: List<IconBannerDsl>): Provider<String>? {
    for (source in sources) {
        when (val state = source.textState) {
            TextState.NotSet -> continue
            TextState.Cleared -> return null
            is TextState.Assigned -> return state.provider
        }
    }
    return null
}

private fun <T : Any> merge(
    sources: List<IconBannerDsl>,
    default: T,
    select: (IconBannerDsl) -> Property<T>,
): Provider<T> {
    var merged: Provider<T>? = null
    for (source in sources) {
        val property = select(source)
        merged = merged?.orElse(property) ?: property
    }
    return merged?.orElse(default) ?: throw IllegalArgumentException("No iconBanner blocks to merge")
}
