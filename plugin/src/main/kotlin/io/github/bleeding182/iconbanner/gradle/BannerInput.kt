package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.api.BannerCorner
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

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

    @get:Input
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

    @get:Input
    val fontFamily: Property<String>

    @get:Input
    val fontWeight: Property<Int>

    @get:Input
    val fontItalic: Property<Boolean>
}
