// Deliberately not this plugin's package: Kotlin build scripts star-import org.gradle.kotlin.dsl.*,
// so `dev { iconBanner { … } }` resolves with no import. Moving this file breaks every build script.
package org.gradle.kotlin.dsl

import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.ProductFlavor
import io.github.bleeding182.iconbanner.gradle.DSL_NAME
import io.github.bleeding182.iconbanner.gradle.IconBannerDsl
import org.gradle.api.plugins.ExtensionAware

/** Configures the icon banner for a single build type. */
fun BuildType.iconBanner(configure: IconBannerDsl.() -> Unit) {
    iconBannerDsl(this).configure()
}

/** Configures the icon banner for a single product flavor. */
fun ProductFlavor.iconBanner(configure: IconBannerDsl.() -> Unit) {
    iconBannerDsl(this).configure()
}

private fun iconBannerDsl(owner: Any): IconBannerDsl {
    val extensions = (owner as? ExtensionAware)?.extensions
        ?: error("$owner does not accept extensions, so it cannot carry an iconBanner block.")
    return extensions.findByName(DSL_NAME) as? IconBannerDsl
        ?: error(
            "No iconBanner block on $owner. Apply the io.github.bleeding182.iconbanner plugin " +
                "before configuring android { }."
        )
}
