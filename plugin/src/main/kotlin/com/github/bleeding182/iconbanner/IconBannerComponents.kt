package com.github.bleeding182.iconbanner

import com.github.bleeding182.iconbanner.api.BannerGenerator
import com.github.bleeding182.iconbanner.api.FontProvider
import java.io.File

/**
 * The single place the Gradle layer reaches for the other two halves of the plugin.
 *
 * Both are currently backed by temporary stubs (see `TemporaryStubs.kt`). Integration is two lines:
 * point [generator] at the real `BannerGenerator` and [fontProvider] at the real `FontProvider`,
 * then delete the stub file.
 */
internal object IconBannerComponents {

    fun generator(): BannerGenerator = StubBannerGenerator()

    fun fontProvider(cacheDir: File, offline: Boolean): FontProvider = StubFontProvider(cacheDir, offline)
}
