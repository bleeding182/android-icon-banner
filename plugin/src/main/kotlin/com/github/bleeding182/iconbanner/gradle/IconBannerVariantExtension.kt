package com.github.bleeding182.iconbanner.gradle

import com.android.build.api.variant.VariantExtension

/**
 * Carries the merged configuration from AGP's extension-creation callback — the only place the
 * build type and product flavor blocks are reachable — over to `onVariants`, where the tasks are
 * wired. [banner] is `null` for a variant that gets no banner.
 */
internal class IconBannerVariantExtension(val banner: ResolvedBanner?) : VariantExtension
