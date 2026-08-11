package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.generator.RasterIcon

/**
 * Colour values must be hex literals. A rasterized icon has the banner composited into its pixels,
 * which needs an actual ARGB value the plugin can parse itself, so the same forms are accepted on every
 * kind of icon alike.
 *
 * Theme attributes get their own message because their failure is the worst of the lot: a launcher
 * inflates the icon from the app's resources with no theme attached, so `?attr/colorPrimary` has
 * nothing to resolve against. The result is a resolution failure and no icon at all — worse than the
 * build error, and impossible to diagnose from the launcher.
 */
internal object ColorFormat {

    /** Returns [value] unchanged, or throws naming [property], [bannerName] and [variantName]. */
    fun check(value: String, property: String, variantName: String, bannerName: String): String {
        // `?colorPrimary` and `?attr/colorPrimary` are both useless here, so the whole prefix goes.
        require(!value.startsWith("?")) {
            "iconBanner.$property for banner '$bannerName' in variant '$variantName' is '$value'. A " +
                "launcher inflates the launcher icon without a theme, so a theme attribute has " +
                "nothing to resolve against and the icon would fail to load. Use a hex literal " +
                "(#RGB, #ARGB, #RRGGBB, #AARRGGBB) instead."
        }
        // The painter's own parser decides, rather than a second regex saying the same thing: what this
        // accepts and what a bitmap fill can actually be painted with have to be one rule, or a build
        // script passes validation here and fails mid-generation.
        require(RasterIcon.parseColor(value) != null) {
            "iconBanner.$property for banner '$bannerName' in variant '$variantName' is '$value', " +
                "which is not a hex colour. Use #RGB, #ARGB, #RRGGBB or #AARRGGBB."
        }
        return value
    }
}
