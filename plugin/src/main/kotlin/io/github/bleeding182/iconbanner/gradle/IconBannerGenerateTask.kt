package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.IconBannerComponents
import io.github.bleeding182.iconbanner.api.BannerCorner
import io.github.bleeding182.iconbanner.api.BannerRequest
import io.github.bleeding182.iconbanner.api.BannerStyle
import io.github.bleeding182.iconbanner.api.GenerationResult
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

/**
 * Writes the bannered copies of the launcher icon into a generated resource directory registered
 * with the variant. Because that directory outranks every source set, the copies replace the
 * originals for this variant only, and the checked-in resources are never touched.
 */
@CacheableTask
abstract class IconBannerGenerateTask : DefaultTask() {

    @get:Input
    abstract val text: Property<String>

    @get:Input
    abstract val color: Property<String>

    @get:Input
    abstract val textColor: Property<String>

    @get:Input
    abstract val corner: Property<BannerCorner>

    @get:Input
    abstract val height: Property<Int>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val fontFile: RegularFileProperty

    /** Ordered highest priority first. Parsed at execution time for the icon resource names. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val manifestFiles: ListProperty<RegularFile>

    /**
     * The variant's *static* resource roots, ordered highest priority first. Never `sources.res.all`
     * — that includes this task's own output directory and forms a dependency cycle.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourceDirectories: ListProperty<Directory>

    /**
     * Project-relative paths of [resourceDirectories], in order. The file fingerprint alone does not
     * capture priority, and priority decides which of two same-named files gets bannered.
     */
    @get:Input
    abstract val resourceDirectoryOrder: ListProperty<String>

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

        // A round icon nobody declared is only a convention. Do not make the generator fail over a
        // resource the user never asked for; a declared one that is missing must still fail.
        val roundIcon = declared.roundIcon
            ?.takeUnless { declared.roundIsFallback && resources.find(it).isEmpty() }

        val style = BannerStyle(
            text = text.get(),
            color = ColorFormat.check(color.get(), "color", variant),
            textColor = ColorFormat.check(textColor.get(), "textColor", variant),
            corner = corner.get(),
            heightPercent = height.get().toDouble(),
        )

        val output = outputDirectory.get().asFile
        fileSystem.delete { delete(output) }
        output.mkdirs()

        val result = IconBannerComponents.generator().generate(
            BannerRequest(
                style = style,
                fontFile = fontFile.get().asFile,
                icon = declared.icon,
                roundIcon = roundIcon,
                resources = resources,
            )
        )

        when (result) {
            // The generator has no idea which variant it was invoked for, and a message that says
            // what went wrong but not where is hard to act on in a build with many variants.
            is GenerationResult.Failure ->
                throw GradleException("icon banner ($variant): ${result.message}")
            is GenerationResult.Success -> {
                for ((relativePath, content) in result.files) {
                    val file = output.resolve(relativePath)
                    file.parentFile?.mkdirs()
                    file.writeText(content)
                }
                // The override is otherwise completely silent — no merger message, no lint warning.
                // This is the only way a user learns their hand-edited icon was replaced.
                for (note in result.info) logger.info("icon banner ($variant): {}", note)
            }
        }
    }
}
