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
    /** Ribbon fill, always a hex literal: a bitmap fill needs a value the plugin can parse itself. */
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
    val content: SourceContent,
) {
    /** Path relative to a resource root, e.g. `drawable-v24/ic_launcher_foreground.xml`. */
    val relativePath: String get() = "$qualifiers/$fileName"

    /** The XML, or null for a bitmap. */
    val xml: String? get() = (content as? SourceContent.Xml)?.text

    /** The pixels, or null for XML. */
    val bytes: ByteArray? get() = (content as? SourceContent.Raster)?.bytes

    /** `ic_launcher.9.png`. The resource name stops at the first dot, so the marker is what is left. */
    val isNinePatch: Boolean get() = fileName.substringBeforeLast('.').endsWith(".9")
}

/** What a source file holds. Two cases, because a bitmap's bytes are as much input as a vector's text. */
internal sealed interface SourceContent {
    data class Xml(val text: String) : SourceContent

    /**
     * Deliberately not a data class: a generated `equals` compares a [ByteArray] by identity, so two
     * reads of the same file would come out unequal and any comparison built on it would lie.
     */
    class Raster(val bytes: ByteArray) : SourceContent
}

/** Read access to the app's resources, with source-set precedence already applied. */
internal interface ResourceLookup {
    /**
     * In no particular order; empty when absent. Includes bitmaps, with their bytes, so "exists but
     * is a bitmap" is distinguishable from "does not exist".
     */
    fun find(ref: ResourceRef): List<SourceResource>
}

/**
 * Makes image readers beyond the JDK's own available. Called once, and only once the JDK has failed to
 * decode a bitmap — so a project whose bitmaps the JDK reads never asks at all.
 *
 * @param resourcePath the file that could not be decoded, for the failure message: it is the only thing
 * that tells the user which resource is at fault.
 */
internal fun interface ImageCodecs {
    fun ensureReadersAvailable(resourcePath: String)
}

/**
 * One generated resource file. A bannered vector comes out as XML, a bannered bitmap as PNG bytes.
 *
 * One type rather than two maps: the generator checks that two icons resolving to the same output
 * path agreed on its content, and that check has to span both kinds or a path could collide with
 * itself unnoticed.
 */
internal sealed interface GeneratedFile {
    data class Text(val content: String) : GeneratedFile

    /** Not a data class, for the same reason as [SourceContent.Raster]. */
    class Binary(val bytes: ByteArray) : GeneratedFile
}

/** Outcome of generating the bannered resources for one variant. */
internal sealed interface GenerationResult {
    /**
     * @param files resource-root-relative path to file, reusing the original qualifier folder.
     * @param info notes worth logging, such as which resources were displaced.
     * @param warnings not worth failing over, but they need a louder log level than [info].
     */
    data class Success(
        val files: Map<String, GeneratedFile>,
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
    /**
     * A no-op by default, which is all an icon graph of pure vectors — or of bitmaps the JDK reads —
     * ever needs. The Gradle layer supplies a real one, whose reader is not on the plugin's own
     * classpath, so the default keeps the generator's tests free of that wiring.
     */
    // `_ ->` spelled out: a bare `{ }` satisfies this one-parameter fun interface through the implicit
    // `it`, so a new parameter on ImageCodecs would keep compiling here and fail at run time with an
    // AbstractMethodError. Named, that becomes a compile error.
    val codecs: ImageCodecs = ImageCodecs { _ -> },
)

/** Supplies a local TrueType file for a [FontSpec], downloading and caching as needed. */
internal interface FontProvider {
    fun resolve(spec: FontSpec): java.io.File
}
