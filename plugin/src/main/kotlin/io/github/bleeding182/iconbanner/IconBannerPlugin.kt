package io.github.bleeding182.iconbanner

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import io.github.bleeding182.iconbanner.gradle.registerIconBanner
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Adds a per-variant banner to the launcher icon.
 *
 * Application modules only; does nothing elsewhere, so a convention plugin may apply it
 * everywhere.
 */
abstract class IconBannerPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.withPlugin("com.android.application") {
            val components = target.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
            registerIconBanner(target, components)
        }
    }
}
