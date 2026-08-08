package io.github.bleeding182.iconbanner

import io.github.bleeding182.iconbanner.api.BannerGenerator
import io.github.bleeding182.iconbanner.api.FontProvider
import io.github.bleeding182.iconbanner.font.GoogleFontProvider
import io.github.bleeding182.iconbanner.generator.DefaultBannerGenerator
import java.io.File

/**
 * The single place the Gradle layer reaches for the other two halves of the plugin.
 *
 * The indirection exists so the Gradle layer depends on the `api` interfaces rather than on
 * concrete implementations, which keeps the generator free of Gradle types and testable without a
 * build.
 */
internal object IconBannerComponents {

    fun generator(): BannerGenerator = DefaultBannerGenerator()

    /**
     * [cacheDir] and [offline] come from the build, not from the provider's own defaults: the
     * provider would otherwise guess the Gradle user home from system properties, which is wrong
     * whenever the build is not using the default one.
     */
    fun fontProvider(cacheDir: File, offline: Boolean): FontProvider =
        GoogleFontProvider(cacheDirectory = cacheDir, offline = offline)
}
