package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.IconBannerComponents
import io.github.bleeding182.iconbanner.api.FontSpec
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

/** One face to fetch. Public for the same mechanical reason as [BannerInput]. */
interface FontInput {

    @get:Input
    val family: Property<String>

    @get:Input
    val weight: Property<Int>

    @get:Input
    val italic: Property<Boolean>
}

/**
 * Produces the TrueType files the generator traces outlines from. The inputs are font identities
 * only, so an incremental build is up to date without touching the network.
 */
@CacheableTask
abstract class IconBannerFontTask : DefaultTask() {

    /**
     * Every face the variant's banners ask for, duplicates included — each entry is a triple of lazy
     * providers, and deduplicating would force them during configuration.
     */
    @get:Nested
    abstract val fonts: ListProperty<FontInput>

    /** Shared across projects under the Gradle user home, so the fetch is paid for once per machine. */
    @get:Internal
    abstract val cacheDirectory: DirectoryProperty

    /** Not an input: `--offline` changes whether a fetch is allowed, never the resulting bytes. */
    @get:Internal
    abstract val offline: Property<Boolean>

    /** One file per distinct face, named by [fontFileName] so the generate task can find each one. */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    protected abstract val fileSystem: FileSystemOperations

    @TaskAction
    fun download() {
        val provider = IconBannerComponents.fontProvider(cacheDirectory.get().asFile, offline.get())
        val output = outputDirectory.get().asFile
        // Cleared first, or a face dropped from the DSL lingers and keeps being served.
        fileSystem.delete { delete(output) }
        output.mkdirs()

        val specs = fonts.get()
            .map { FontSpec(it.family.get(), it.weight.get(), it.italic.get()) }
            .distinct()
        for (spec in specs) {
            provider.resolve(spec).copyTo(output.resolve(fontFileName(spec)), overwrite = true)
        }
    }
}
