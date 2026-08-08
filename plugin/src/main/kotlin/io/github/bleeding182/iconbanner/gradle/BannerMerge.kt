package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.api.BannerCorner
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

/** Values used when no block in the precedence chain sets one. */
internal object BannerDefaults {
    const val COLOR = "#FF0000"
    const val TEXT_COLOR = "#FFFFFF"

    /** Opaque: a themed banner looks like the rest of the icon until someone asks for a shade. */
    const val MONOCHROME_ALPHA = 100

    val CORNER = BannerCorner.TOP_LEFT

    /**
     * 13 only decides how two- and three-character markers look; longer text is length-bound anyway.
     * 1.5 leaves a quarter of the cap height clear above and below.
     */
    const val MAX_TEXT_SIZE = 13
    const val LINE_HEIGHT = 1.5

    /**
     * See [io.github.bleeding182.iconbanner.generator.Ribbon.DEFAULT_POSITION_PERCENT], which a
     * test ties this to.
     */
    const val POSITION = 65

    const val FONT = "Roboto Mono"
    const val WEIGHT = 700
    const val ITALIC = false

    /** Everything on one level until someone says otherwise; ties fall back to declaration order. */
    const val Z = 0
}

/**
 * One merged banner for one variant. Every value is still lazy; nothing here has been evaluated.
 */
internal class ResolvedBanner(
    /** The DSL name; `main` for the one made of the blocks' own properties. */
    val name: String,
    val text: Provider<String>,
    val color: Provider<String>,
    val textColor: Provider<String>,
    val monochromeAlpha: Provider<Int>,
    val corner: Provider<BannerCorner>,
    val position: Provider<Int>,
    val maxTextSize: Provider<Int>,
    val lineHeight: Provider<Double>,
    val z: Provider<Int>,
    val fontFamily: Provider<String>,
    val fontWeight: Provider<Int>,
    val fontItalic: Provider<Boolean>,
)

/**
 * Merges the blocks applying to one variant into every banner it gets. Empty means no tasks.
 *
 * [sources] must be in AGP's precedence order: build type, product flavors in dimension order,
 * then the project block. `main` always comes first, having no declared position to observe.
 */
internal fun mergeBanner(sources: List<IconBannerDsl>): List<ResolvedBanner> {
    val main = mergeOne(MAIN_BANNER, textSources = sources, all = sources)
    val named = declarationOrder(sources).mapNotNull { name ->
        val own: List<IconBannerOptions> = sources.mapNotNull { it.banners.findByName(name) }
        mergeOne(name, textSources = own, all = own + sources)
    }
    return listOfNotNull(main) + named
}

/**
 * Every declared name, deduped on first appearance.
 *
 * Walked backwards, lowest precedence first, so a project-block banner paints behind one a flavor
 * added on top. Multi-dimension flavors therefore come out in reverse dimension order.
 */
private fun declarationOrder(sources: List<IconBannerDsl>): List<String> =
    sources.asReversed().flatMapTo(LinkedHashSet()) { it.bannerNames }.toList()

/**
 * One banner, or `null` when nothing turned it on.
 *
 * [textSources] is where `text` may come from — for a named banner its own declarations alone,
 * since a block-level `text` belongs to `main`. [all] is every source for everything else.
 */
private fun mergeOne(
    name: String,
    textSources: List<IconBannerOptions>,
    all: List<IconBannerOptions>,
): ResolvedBanner? {
    val text = resolveText(textSources) ?: return null
    return ResolvedBanner(
        name = name,
        text = text,
        color = merge(all, BannerDefaults.COLOR) { it.color },
        textColor = merge(all, BannerDefaults.TEXT_COLOR) { it.textColor },
        monochromeAlpha = merge(all, BannerDefaults.MONOCHROME_ALPHA) { it.monochromeAlpha },
        corner = merge(all, BannerDefaults.CORNER) { it.corner },
        position = merge(all, BannerDefaults.POSITION) { it.position },
        maxTextSize = merge(all, BannerDefaults.MAX_TEXT_SIZE) { it.maxTextSize },
        lineHeight = merge(all, BannerDefaults.LINE_HEIGHT) { it.lineHeight },
        z = merge(all, BannerDefaults.Z) { it.z },
        fontFamily = merge(all, BannerDefaults.FONT) { it.font },
        fontWeight = merge(all, BannerDefaults.WEIGHT) { it.weight },
        fontItalic = merge(all, BannerDefaults.ITALIC) { it.italic },
    )
}

/** The winning `text`, or `null`. From assignment state alone, so a provider is never forced. */
private fun resolveText(sources: List<IconBannerOptions>): Provider<String>? {
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
    sources: List<IconBannerOptions>,
    default: T,
    select: (IconBannerOptions) -> Property<T>,
): Provider<T> {
    var merged: Provider<T>? = null
    for (source in sources) {
        val property = select(source)
        merged = merged?.orElse(property) ?: property
    }
    return merged?.orElse(default) ?: throw IllegalArgumentException("No iconBanner blocks to merge")
}
