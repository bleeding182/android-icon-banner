package io.github.bleeding182.iconbanner.api

/**
 * Shared vocabulary between the Gradle/AGP layer, the font layer and the pure generator.
 *
 * **Nothing in this package may reference Gradle or AGP types.** The generator is exercised in tests
 * without a build, and that only stays true if this boundary holds.
 *
 * Everything is `internal` except [BannerCorner]: a plugin's public classes land on every consuming
 * buildscript's classpath and become a binary-compatibility promise. [BannerCorner] is public
 * because the DSL hands it to build scripts.
 */

/**
 * Shared vocabulary between the Gradle/AGP layer, the font layer and the generator.
 *
 * **Nothing here may reference Gradle or AGP types**, or the generator stops being testable
 * without a build. Everything is `internal` except [BannerCorner], which the DSL hands to build
 * scripts: a plugin's public classes are a binary-compatibility promise.
 */
enum class BannerCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/** Identifies a Google Font face. Maps onto the `wght` and `ital` axes of the CSS API. */
internal data class FontSpec(
    val family: String,
    val weight: Int,
    val italic: Boolean,
)

/** A fully resolved banner appearance. Every value here has already been merged and defaulted. */
internal data class BannerStyle(
    /** As named in the DSL, `main` for the block's own properties. For messages only. */
    val name: String,
    /** Rendered verbatim. May be empty, which means a ribbon with no text. */
    val text: String,
    /** Ribbon fill. A hex literal, or a `@color/...` reference passed straight through. */
    val color: String,
    /** Text fill, same accepted forms as [color]. */
    val textColor: String,
    /** Opacity of the band in the themed icon, 0..100. The system supplies the colour. */
    val monochromeAlphaPercent: Double,
    val corner: BannerCorner,
    /** How far out the band sits: 0 the icon's centre, 100 where no text fits. */
    val positionPercent: Double,
    /** Cap height as a percentage of the icon's edge. An upper bound; the text may come out smaller. */
    val maxTextSizePercent: Double,
    /** Band thickness as a multiple of the cap height. Cosmetic: it never enters the fit. */
    val lineHeight: Double,
)

/** An Android resource reference, e.g. `@mipmap/ic_launcher` becomes `ResourceRef("mipmap", "ic_launcher")`. */
internal data class ResourceRef(val type: String, val name: String) {
    override fun toString(): String = "@$type/$name"

    companion object {
        /** Parses `@mipmap/ic_launcher`, `@android:drawable/x` and bare `mipmap/ic_launcher`. */
        fun parse(reference: String): ResourceRef? {
            val body = reference.removePrefix("@").substringAfter(':')
            val type = body.substringBefore('/', missingDelimiterValue = "")
            val name = body.substringAfter('/', missingDelimiterValue = "")
            return if (type.isEmpty() || name.isEmpty()) null else ResourceRef(type, name)
        }
    }
}

/** One file backing a resource, in one qualifier folder. A resource typically has several. */
internal data class SourceResource(
    /** The resource folder name, e.g. `drawable`, `drawable-v24`, `mipmap-anydpi-v26`. */
    val qualifiers: String,
    /** The file name including extension, e.g. `ic_launcher_foreground.xml`. */
    val fileName: String,
    /** File contents, or null when the file is not XML (a webp or png). */
    val xml: String?,
) {
    /** Path relative to a resource root, e.g. `drawable-v24/ic_launcher_foreground.xml`. */
    val relativePath: String get() = "$qualifiers/$fileName"
}

/** Read access to the app's resources, with source-set precedence already applied. */
internal interface ResourceLookup {
    /**
     * In no particular order; empty when absent. Includes non-XML files, so "exists but is a
     * bitmap" is distinguishable from "does not exist".
     */
    fun find(ref: ResourceRef): List<SourceResource>
}

/** Outcome of generating the bannered resources for one variant. */
internal sealed interface GenerationResult {
    /**
     * @param files resource-root-relative path to content, reusing the original qualifier folder.
     * @param info notes worth logging, such as which resources were displaced.
     * @param warnings not worth failing over, but they need a louder log level than [info].
     */
    data class Success(
        val files: Map<String, String>,
        val info: List<String> = emptyList(),
        val warnings: List<String> = emptyList(),
    ) : GenerationResult

    /** The banner could not be produced. [message] is shown to the user as the build failure. */
    data class Failure(val message: String) : GenerationResult
}

/** The pure seam. No Gradle, no network, no filesystem writes. */
internal interface BannerGenerator {
    fun generate(request: BannerRequest): GenerationResult
}

/**
 * One banner to paint, with the face it is drawn in — each names its own family and weight.
 *
 * Not an adaptive icon's layers; every [BannerLayer] is painted onto each of those.
 */
internal data class BannerLayer(
    val style: BannerStyle,
    /** A local TrueType file. Already downloaded; the generator never fetches anything. */
    val fontFile: java.io.File,
)

/** Everything [BannerGenerator] needs. */
internal data class BannerRequest(
    /** Painted in list order: the first entry is furthest back. Never empty. */
    val layers: List<BannerLayer>,
    /** What the manifest's `android:icon` points at. */
    val icon: ResourceRef,
    /** What `android:roundIcon` points at, or null when absent. */
    val roundIcon: ResourceRef?,
    val resources: ResourceLookup,
)

/** Supplies a local TrueType file for a [FontSpec], downloading and caching as needed. */
internal interface FontProvider {
    fun resolve(spec: FontSpec): java.io.File
}
