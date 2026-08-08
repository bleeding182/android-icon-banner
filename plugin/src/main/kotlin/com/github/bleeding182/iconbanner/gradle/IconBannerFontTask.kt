package com.github.bleeding182.iconbanner.gradle

import com.github.bleeding182.iconbanner.IconBannerComponents
import com.github.bleeding182.iconbanner.api.FontSpec
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Produces the TrueType file the generator traces glyph outlines from.
 *
 * The inputs are only the font identity, so an incremental build is up to date and makes no network
 * call at all — the point of having this as a separate task rather than fetching inside the
 * generate task.
 */
@CacheableTask
abstract class IconBannerFontTask : DefaultTask() {

    @get:Input
    abstract val family: Property<String>

    @get:Input
    abstract val weight: Property<Int>

    @get:Input
    abstract val italic: Property<Boolean>

    /** Shared across projects under the Gradle user home, so the fetch is paid for once per machine. */
    @get:Internal
    abstract val cacheDirectory: DirectoryProperty

    /** Not an input: `--offline` changes whether a fetch is allowed, never the resulting bytes. */
    @get:Internal
    abstract val offline: Property<Boolean>

    @get:OutputFile
    abstract val fontFile: RegularFileProperty

    @TaskAction
    fun download() {
        val provider = IconBannerComponents.fontProvider(cacheDirectory.get().asFile, offline.get())
        val resolved = provider.resolve(FontSpec(family.get(), weight.get(), italic.get()))
        val target = fontFile.get().asFile
        target.parentFile?.mkdirs()
        resolved.copyTo(target, overwrite = true)
    }
}
