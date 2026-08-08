package com.github.bleeding182.iconbanner

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Scaffolding. The AGP wiring lands here: DSL registration, per-variant configuration merge,
 * icon discovery and the generate/download tasks.
 */
abstract class IconBannerPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        // Intentionally empty until the AGP integration lands.
    }
}
