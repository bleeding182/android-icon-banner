package com.github.bleeding182.iconbanner.gradle

import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.ProductFlavor
import org.gradle.api.plugins.ExtensionAware

/**
 * Kotlin DSL accessors for the build-type and product-flavor `iconBanner { }` blocks.
 *
 * Gradle generates a type-safe accessor for the project-level block on `ApplicationExtension`, but
 * not for container elements, so without these an `iconBanner { }` written inside
 * `productFlavors { dev { } }` resolves against the enclosing `android { }` receiver instead and
 * silently configures the project defaults — every variant would get the banner.
 *
 * Groovy build scripts need none of this; `ExtensionAware` already answers `iconBanner` dynamically.
 */
fun BuildType.iconBanner(configure: IconBannerDsl.() -> Unit) {
    iconBannerDsl(this).configure()
}

/** See [BuildType.iconBanner]. */
fun ProductFlavor.iconBanner(configure: IconBannerDsl.() -> Unit) {
    iconBannerDsl(this).configure()
}

private fun iconBannerDsl(owner: Any): IconBannerDsl {
    val extensions = (owner as? ExtensionAware)?.extensions
        ?: error("$owner does not accept extensions, so it cannot carry an iconBanner block.")
    return extensions.findByName(DSL_NAME) as? IconBannerDsl
        ?: error(
            "No iconBanner block on $owner. Apply the com.github.bleeding182.iconbanner plugin " +
                "before configuring android { }."
        )
}
