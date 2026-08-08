package io.github.bleeding182.iconbanner

import io.github.bleeding182.iconbanner.api.BannerGenerator
import io.github.bleeding182.iconbanner.api.FontProvider
import io.github.bleeding182.iconbanner.font.GoogleFontProvider
import io.github.bleeding182.iconbanner.generator.DefaultBannerGenerator
import java.io.File

/**
 * Where the Gradle layer reaches the other two halves, so it depends on the `api` interfaces
 * rather than on implementations.
 */
internal object IconBannerComponents {

    fun generator(): BannerGenerator = DefaultBannerGenerator()

    /** From the build, not the provider's defaults, which would guess the Gradle user home. */
    fun fontProvider(cacheDir: File, offline: Boolean): FontProvider =
        GoogleFontProvider(cacheDirectory = cacheDir, offline = offline)
}
