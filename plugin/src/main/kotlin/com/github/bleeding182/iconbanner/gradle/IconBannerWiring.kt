package com.github.bleeding182.iconbanner.gradle

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.DslExtension
import com.android.build.api.variant.VariantExtensionConfig
import org.gradle.api.Project

/** The name of the `iconBanner { }` block in all three DSL slots. */
internal const val DSL_NAME = "iconBanner"

/**
 * Registers the DSL in AGP's three slots and wires the tasks.
 *
 * AGP's builder offers exactly project, build type and product flavor — there is no `defaultConfig`
 * slot, so the block inside `android { }` is the defaults slot.
 */
internal fun registerIconBanner(project: Project, components: ApplicationAndroidComponentsExtension) {
    components.registerExtension(
        DslExtension.Builder(DSL_NAME)
            .extendProjectWith(IconBannerDsl::class.java)
            .extendBuildTypeWith(IconBannerDsl::class.java)
            .extendProductFlavorWith(IconBannerDsl::class.java)
            .build()
    ) { config ->
        IconBannerVariantExtension(mergeBanner(precedenceChain(config)))
    }

    components.onVariants(components.selector().all()) { variant ->
        // Nested components — the androidTest APK above all — get no banner.
        configureVariant(project, variant)
    }
}

/**
 * Build type, then product flavors in dimension order, then the project-level defaults. AGP returns
 * the flavor extensions in the same order as [ApplicationVariant.productFlavors], which is dimension
 * order, so the first dimension wins.
 */
private fun precedenceChain(config: VariantExtensionConfig<out ApplicationVariant>): List<IconBannerDsl> =
    buildList {
        config.buildTypeExtension(IconBannerDsl::class.java)?.let(::add)
        addAll(config.productFlavorsExtensions(IconBannerDsl::class.java).filterNotNull())
        config.projectExtension(IconBannerDsl::class.java)?.let(::add)
    }

private fun configureVariant(project: Project, variant: ApplicationVariant) {
    // Enablement was decided at configuration time from assignment alone, so a variant with no
    // banner never gets a task registered.
    val banner = variant.getExtension(IconBannerVariantExtension::class.java)?.banner ?: return

    val res = variant.sources.res
    if (res == null) {
        project.logger.warn(
            "iconBanner: variant '${variant.name}' asked for a banner but android resources are " +
                "disabled for it, so there is nowhere to put one."
        )
        return
    }

    val suffix = variant.name.replaceFirstChar { it.uppercaseChar() }
    val projectDir = project.layout.projectDirectory.asFile
    val fontCache = fontCacheDirectory(project)
    val isOffline = project.gradle.startParameter.isOffline
    val manifests = variant.sources.manifests.all.map { files -> files.toList() }
    val staticRes = res.static.map { layers -> layers.flatten() }
    val variantNameValue = variant.name

    val fontTask = project.tasks.register("download${suffix}IconBannerFont", IconBannerFontTask::class.java)
    fontTask.configure {
        group = TASK_GROUP
        description = "Fetches the TrueType face for the $variantNameValue icon banner."
        family.set(banner.fontFamily)
        weight.set(banner.fontWeight)
        italic.set(banner.fontItalic)
        cacheDirectory.set(fontCache)
        offline.set(isOffline)
        fontFile.set(
            project.layout.buildDirectory.file("intermediates/icon_banner/font/$variantNameValue/banner.ttf")
        )
    }

    val generateTask = project.tasks.register("generate${suffix}IconBanner", IconBannerGenerateTask::class.java)
    generateTask.configure {
        group = TASK_GROUP
        description = "Generates the bannered launcher icon resources for $variantNameValue."
        variantName.set(variantNameValue)
        text.set(banner.text)
        color.set(banner.color)
        textColor.set(banner.textColor)
        corner.set(banner.corner)
        height.set(banner.height)
        fontFile.set(fontTask.flatMap { it.fontFile })
        manifestFiles.set(manifests)
        resourceDirectories.set(staticRes)
        resourceDirectoryOrder.set(
            staticRes.map { dirs -> dirs.map { it.asFile.relativeToOrSelf(projectDir).invariantPath() } }
        )
    }

    res.addGeneratedSourceDirectory(generateTask) { it.outputDirectory }
}

private fun java.io.File.invariantPath(): String = path.replace('\\', '/')

/**
 * Where downloaded fonts live. Shared across projects and outliving `clean`, because the design
 * deliberately bundles no fonts at all — the cache is what keeps "always download" from hurting.
 *
 * [FONT_CACHE_PROPERTY] overrides the location. CI can point it at a warmed, restorable directory,
 * and the plugin's own tests use it to stay off the network without writing into the developer's
 * real cache.
 */
private fun fontCacheDirectory(project: org.gradle.api.Project): java.io.File {
    val override = project.providers.gradleProperty(FONT_CACHE_PROPERTY).orNull
    return if (override != null) {
        project.layout.projectDirectory.dir(override).asFile
    } else {
        project.gradle.gradleUserHomeDir.resolve("caches/android-icon-banner/fonts")
    }
}

internal const val FONT_CACHE_PROPERTY = "iconbanner.fontCacheDir"

private const val TASK_GROUP = "icon banner"
