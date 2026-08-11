package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.IconBannerComponents
import io.github.bleeding182.iconbanner.api.BannerLayer
import io.github.bleeding182.iconbanner.api.BannerRequest
import io.github.bleeding182.iconbanner.api.BannerStyle
import io.github.bleeding182.iconbanner.api.FontSpec
import io.github.bleeding182.iconbanner.api.GeneratedFile
import io.github.bleeding182.iconbanner.api.GenerationResult
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

/**
 * Writes bannered copies of the launcher icon into a generated resource directory. That directory
 * outranks every source set, so the copies replace the originals for this variant only.
 */
@CacheableTask
abstract class IconBannerGenerateTask : DefaultTask() {

    /** In declaration order, never empty. Paint order needs `z`, which is lazy, so [generate] sorts. */
    @get:Nested
    abstract val banners: ListProperty<BannerInput>

    /** [IconBannerFontTask]'s output. Each banner picks its own face out of it by [fontFileName]. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val fontDirectory: DirectoryProperty

    /** Ordered highest priority first. Parsed at execution time for the icon resource names. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val manifestFiles: ListProperty<RegularFile>

    /**
     * Highest priority first. Never `sources.res.all` — that includes this task's own output and
     * forms a dependency cycle.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourceDirectories: ListProperty<Directory>

    /**
     * The file fingerprint does not capture priority, and priority decides which of two same-named
     * files gets bannered.
     */
    @get:Input
    abstract val resourceDirectoryOrder: ListProperty<String>

    /**
     * The readers for bitmap icons, `@Internal` on purpose: as an input it would be fingerprinted, and
     * so resolved, on every run of this task — including for a project whose icons are all vectors and
     * which decodes nothing at all. The collection stays lazy for the same reason, and resolution then
     * waits for the first bitmap the JDK cannot read. The configuration cache is the exception, and
     * [imageReaderFiles] records what that costs and why the view is lenient.
     *
     * [imageReaderCoordinates] is the tracked half of the pair, so a change of reader version still
     * refingerprints the task even though the files themselves are not watched. On its own each half
     * looks wrong.
     */
    @get:Internal
    abstract val imageReaderClasspath: ConfigurableFileCollection

    /** @see imageReaderClasspath */
    @get:Input
    abstract val imageReaderCoordinates: ListProperty<String>

    @get:Input
    abstract val variantName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    protected abstract val fileSystem: FileSystemOperations

    @TaskAction
    fun generate() {
        val variant = variantName.get()
        val roots = resourceDirectories.get().map { it.asFile }
        val resources = DirectoryResourceLookup(roots)
        val declared = ManifestIcons.read(manifestFiles.get().map { it.asFile })
        val roundIcon = declared.roundIconToBanner(resources)
        val fonts = fontDirectory.get()

        val layers = banners.get()
            // A stable sort, so declaration order *is* the z tie-break.
            .sortedBy { it.z.get() }
            .map { it.toLayer(variant, fonts) }

        val output = outputDirectory.get().asFile
        fileSystem.delete { delete(output) }
        output.mkdirs()

        val result = IconBannerComponents.generator().generate(
            BannerRequest(
                layers = layers,
                icon = declared.icon,
                roundIcon = roundIcon,
                resources = resources,
                codecs = ClasspathImageCodecs(variant, imageReaderCoordinates.get()) {
                    imageReaderClasspath.files
                },
            )
        )

        when (result) {
            // The generator does not know its variant, and the message has to say where.
            is GenerationResult.Failure ->
                throw GradleException("icon banner ($variant): ${result.message}")
            is GenerationResult.Success -> {
                for ((relativePath, generated) in result.files) {
                    val file = output.resolve(relativePath)
                    file.parentFile?.mkdirs()
                    when (generated) {
                        is GeneratedFile.Text -> file.writeText(generated.content)
                        is GeneratedFile.Binary -> file.writeBytes(generated.bytes)
                    }
                }
                // Lifecycle, not info: bannering a release build unnoticed is the worst thing this can do.
                logger.lifecycle(
                    "icon banner: variant '{}' replaces {} with a bannered copy: {}",
                    variant,
                    declared.icon,
                    layers.joinToString(", ") { "${it.style.name} = \"${it.style.text}\"" },
                )
                for (note in result.info) logger.info("icon banner ($variant): {}", note)
                for (warning in result.warnings) logger.warn("icon banner ($variant): {}", warning)
            }
        }
    }

    /**
     * Validated here rather than in the DSL because a `Provider` colour cannot be checked without
     * forcing it.
     */
    private fun BannerInput.toLayer(variant: String, fonts: Directory): BannerLayer {
        val banner = name.get()
        val maxTextSizeValue = maxTextSize.get()
        val lineHeightValue = lineHeight.get()
        val positionValue = position.get()
        val monochromeAlphaValue = monochromeAlpha.get()
        BannerBounds.check(
            maxTextSizeValue, lineHeightValue, positionValue, monochromeAlphaValue, variant, banner,
        )
        val face = FontSpec(fontFamily.get(), fontWeight.get(), fontItalic.get())
        return BannerLayer(
            style = BannerStyle(
                name = banner,
                text = text.get(),
                color = ColorFormat.check(color.get(), "color", variant, banner),
                textColor = ColorFormat.check(textColor.get(), "textColor", variant, banner),
                monochromeAlphaPercent = monochromeAlphaValue.toDouble(),
                corner = corner.get(),
                positionPercent = positionValue.toDouble(),
                maxTextSizePercent = maxTextSizeValue.toDouble(),
                lineHeight = lineHeightValue,
            ),
            fontFile = fonts.file(fontFileName(face)).asFile,
        )
    }
}
