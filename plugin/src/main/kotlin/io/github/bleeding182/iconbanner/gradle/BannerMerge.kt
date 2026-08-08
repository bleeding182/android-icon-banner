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
     * Cap height as a percentage of the icon's shorter edge, and the band as a multiple of it.
     *
     * Chosen for continuity with the single band-width knob these replaced: its default of 20
     * produced a 21.6-unit band on a 108 icon with 13.8 units of that available to the text, a ratio
     * of 1.56. 13 and 1.5 put a 14.04-unit cap height in a 21.06-unit band, so short text and the
     * band around it both come out within about 2% of where they used to be.
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
