package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.api.BannerCorner
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional

/**
 * One banner, as a task input. A managed type: Gradle implements it.
 *
 * Public where the rest of the model is `internal`, and it has to be: a public task property
 * cannot expose an internal Kotlin type, and Kotlin mangles internal accessors. Not for build
 * scripts — the DSL is [IconBannerDsl].
 */
interface BannerInput {

    /** The DSL name, carried through only so failures and warnings can point at it. */
    @get:Input
    val name: Property<String>

    /**
     * Optional only so that a provider with no value reaches the task, which fails naming the banner:
     * Gradle's own validation names neither it nor the variant. Never a way to ask for no banner.
     */
    @get:Input
    @get:Optional
    val text: Property<String>

    @get:Input
    val color: Property<String>

    @get:Input
    val textColor: Property<String>

    @get:Input
    val monochromeAlpha: Property<Int>

    @get:Input
    val corner: Property<BannerCorner>

    @get:Input
    val position: Property<Int>

    @get:Input
    val maxTextSize: Property<Int>

    @get:Input
    val lineHeight: Property<Double>

    /** Higher is painted later. An input, not merely a hint: it decides the bytes that come out. */
    @get:Input
    val z: Property<Int>

    /** The same object the font task is handed, so the face fetched is the face drawn with. */
    @get:Nested
    val font: FontInput
}

/** One face to fetch. Public for the same mechanical reason as [BannerInput]. */
interface FontInput {

    @get:Input
    val family: Property<String>

    @get:Input
    val weight: Property<Int>

    @get:Input
    val italic: Property<Boolean>
}
