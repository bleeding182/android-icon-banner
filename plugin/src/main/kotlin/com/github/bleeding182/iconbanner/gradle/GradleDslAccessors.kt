// Deliberately not this plugin's own package.
//
// Kotlin build scripts implicitly star-import `org.gradle.kotlin.dsl.*` — that is how `configure`,
// `named` and friends work without an import line. Declaring these accessors here makes them
// available in every `.gradle.kts` with no import at all.
//
// The import is not cosmetic. Gradle generates a type-safe accessor for the project-level block on
// `ApplicationExtension`, but none for container elements. Without a candidate on the inner
// receiver, an `iconBanner { }` written inside `productFlavors { dev { } }` still *compiles* — it
// binds to the enclosing `android { }` receiver and silently configures the project-wide defaults,
// so every variant gets a banner. That reads as a plugin bug, not as a missing import.
//
// Kotlin resolves the innermost receiver first, so once these exist the flavor-level call wins.
//
// Groovy scripts need none of this; `ExtensionAware` answers `iconBanner` dynamically.
package org.gradle.kotlin.dsl

import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.ProductFlavor
import com.github.bleeding182.iconbanner.gradle.DSL_NAME
import com.github.bleeding182.iconbanner.gradle.IconBannerDsl
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
            "No iconBanner block on $owner. Apply the com.github.bleeding182.iconbanner plugin " +
                "before configuring android { }."
        )
}
