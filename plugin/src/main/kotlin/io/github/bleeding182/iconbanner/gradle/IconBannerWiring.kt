package io.github.bleeding182.iconbanner.gradle

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.DslExtension
import com.android.build.api.variant.VariantExtensionConfig
import org.gradle.api.GradleException
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
    // Before anything touches the AGP 9 variant API: on AGP 8 the first call dies with a linkage
    // error naming an internal class. pluginVersion itself has been safe since AGP 7.
    val version = components.pluginVersion
    unsupportedAgpMessage(version.major, version.minor, version.micro)?.let { throw GradleException(it) }

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
        configureVariant(project, variant)
    }
}

/**
 * Why this AGP is too old, or null when it will do.
 *
 * Takes the version apart rather than an [com.android.build.api.AndroidPluginVersion], so the rule is
 * unit-testable — AGP is `compileOnly`, so no AGP type exists on the test classpath. Previews of the
 * minimum are accepted, where `AndroidPluginVersion` would sort `9.3.0-alpha01` below `9.3.0`.
 */
internal fun unsupportedAgpMessage(major: Int, minor: Int, micro: Int): String? {
    if (major > MINIMUM_AGP_MAJOR || (major == MINIMUM_AGP_MAJOR && minor >= MINIMUM_AGP_MINOR)) return null
    return "The icon banner plugin needs Android Gradle Plugin $MINIMUM_AGP_MAJOR.$MINIMUM_AGP_MINOR " +
        "or newer, but this build uses $major.$minor.$micro. It is written against the AGP 9 variant " +
        "API with no compatibility shims, so on an older AGP it fails with a class-loading error " +
        "rather than anything actionable. Upgrade AGP, or remove the plugin from this module."
}

private const val MINIMUM_AGP_MAJOR = 9
private const val MINIMUM_AGP_MINOR = 3

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
    // Decided at configuration time from assignment alone.
    val resolved = variant.getExtension(IconBannerVariantExtension::class.java)?.banners.orEmpty()
    if (resolved.isEmpty()) return

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

    // One pair of tasks per variant, not per banner: they rewrite the same icon resources.
    val objects = project.objects
    val fontInputs = resolved.map { banner ->
        objects.newInstance(FontInput::class.java).apply {
            family.set(banner.fontFamily)
            weight.set(banner.fontWeight)
            italic.set(banner.fontItalic)
        }
    }
    val bannerInputs = resolved.map { banner ->
        objects.newInstance(BannerInput::class.java).apply {
            name.set(banner.name)
            text.set(banner.text)
            color.set(banner.color)
            textColor.set(banner.textColor)
            monochromeAlpha.set(banner.monochromeAlpha)
            corner.set(banner.corner)
            position.set(banner.position)
            maxTextSize.set(banner.maxTextSize)
            lineHeight.set(banner.lineHeight)
            z.set(banner.z)
            fontFamily.set(banner.fontFamily)
            fontWeight.set(banner.fontWeight)
            fontItalic.set(banner.fontItalic)
        }
    }

    val fontTask = project.tasks.register("download${suffix}IconBannerFont", IconBannerFontTask::class.java)
    fontTask.configure {
        group = TASK_GROUP
        description = "Fetches the TrueType faces for the $variantNameValue icon banners."
        fonts.set(fontInputs)
        cacheDirectory.set(fontCache)
        offline.set(isOffline)
        outputDirectory.set(
            project.layout.buildDirectory.dir("intermediates/icon_banner/font/$variantNameValue")
        )
    }

    val generateTask = project.tasks.register("generate${suffix}IconBanner", IconBannerGenerateTask::class.java)
    generateTask.configure {
        group = TASK_GROUP
        description = "Generates the bannered launcher icon resources for $variantNameValue."
        variantName.set(variantNameValue)
        banners.set(bannerInputs)
        fontDirectory.set(fontTask.flatMap { it.outputDirectory })
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
 * Where downloaded fonts live. Shared across projects and outliving `clean`, since the plugin bundles
 * no fonts at all. [FONT_CACHE_PROPERTY] overrides it, for CI and for keeping tests off the network.
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
