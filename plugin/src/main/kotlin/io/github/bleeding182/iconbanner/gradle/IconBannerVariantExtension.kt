package io.github.bleeding182.iconbanner.gradle

import com.android.build.api.variant.VariantExtension

/**
 * Carries the merged configuration from AGP's extension-creation callback — the only place the
 * build type and flavor blocks are reachable — over to `onVariants`.
 */
internal class IconBannerVariantExtension(val banners: List<ResolvedBanner>) : VariantExtension
