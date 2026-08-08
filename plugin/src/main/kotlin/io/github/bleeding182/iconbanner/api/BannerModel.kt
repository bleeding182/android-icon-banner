package io.github.bleeding182.iconbanner.api

/**
 * Shared vocabulary between the three parts of the plugin: the Gradle/AGP layer that resolves
 * configuration and wires tasks, the font layer that produces a TrueType file, and the pure
 * generator that turns all of it into resource XML.
 *
 * Nothing in this package may reference Gradle or AGP types. The generator is exercised in tests
 * without a build, and that only stays true if this boundary holds.
 */

/** Which corner of the icon the ribbon occupies. */
enum class BannerCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/** Identifies a Google Font face. Maps onto the `wght` and `ital` axes of the CSS API. */
data class FontSpec(
    val family: String,
    val weight: Int,
    val italic: Boolean,
)

/** A fully resolved banner appearance. Every value here has already been merged and defaulted. */
data class BannerStyle(
    /** Rendered verbatim. May be empty, which means a ribbon with no text. */
    val text: String,
    /** Ribbon fill. A hex literal, or a `@color/...` / `?attr/...` reference passed straight through. */
    val color: String,
    /** Text fill, same accepted forms as [color]. */
    val textColor: String,
    val corner: BannerCorner,
    /** Ribbon band width as a percentage of the icon's edge length. */
    val heightPercent: Double,
)

/** An Android resource reference, e.g. `@mipmap/ic_launcher` becomes `ResourceRef("mipmap", "ic_launcher")`. */
data class ResourceRef(val type: String, val name: String) {
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

/**
 * One file backing a resource, in one qualifier folder.
 *
 * A resource typically has several of these: `drawable/ic_launcher_foreground.xml` and
 * `drawable-v24/ic_launcher_foreground.xml` are two [SourceResource]s of the same [ResourceRef].
 */
data class SourceResource(
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

/**
 * Read access to the app's resources, with source-set precedence already applied by the caller.
 *
 * Implemented over real directories by the Gradle layer, and over a plain map by tests.
 */
interface ResourceLookup {
    /**
     * Every qualifier variant backing [ref], in no particular order. Empty when the resource does
     * not exist. Includes non-XML files, so callers can tell "exists but is a bitmap" apart from
     * "does not exist".
     */
    fun find(ref: ResourceRef): List<SourceResource>
}

/** Outcome of generating the bannered resources for one variant. */
sealed interface GenerationResult {
    /**
     * @param files resource-root-relative path to file content, ready to be written into the
     *   generated resource directory. Paths reuse the original qualifier folder.
     * @param info human-readable notes worth logging, such as which resources were displaced.
     */
    data class Success(
        val files: Map<String, String>,
        val info: List<String> = emptyList(),
    ) : GenerationResult

    /** The banner could not be produced. [message] is shown to the user as the build failure. */
    data class Failure(val message: String) : GenerationResult
}

/**
 * The pure seam. Given a style, a font file and read access to the app's resources, produces the
 * complete set of resource files to emit. No Gradle, no network, no filesystem writes.
 */
interface BannerGenerator {
    fun generate(request: BannerRequest): GenerationResult
}

/** Everything [BannerGenerator] needs. */
data class BannerRequest(
    val style: BannerStyle,
    /** A local TrueType file. Already downloaded; the generator never fetches anything. */
    val fontFile: java.io.File,
    /** What the manifest's `android:icon` points at. */
    val icon: ResourceRef,
    /** What `android:roundIcon` points at, or null when absent. */
    val roundIcon: ResourceRef?,
    val resources: ResourceLookup,
)

/** Supplies a local TrueType file for a [FontSpec], downloading and caching as needed. */
interface FontProvider {
    fun resolve(spec: FontSpec): java.io.File
}
