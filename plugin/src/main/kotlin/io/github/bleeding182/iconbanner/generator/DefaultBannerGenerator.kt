package io.github.bleeding182.iconbanner.generator

import io.github.bleeding182.iconbanner.api.BannerGenerator
import io.github.bleeding182.iconbanner.api.BannerRequest
import io.github.bleeding182.iconbanner.api.GeneratedFile
import io.github.bleeding182.iconbanner.api.GenerationResult
import io.github.bleeding182.iconbanner.api.ResourceRef
import io.github.bleeding182.iconbanner.api.SourceContent
import io.github.bleeding182.iconbanner.api.SourceResource
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.awt.image.BufferedImage

/**
 * Launcher icon references in, resource file contents out. Free of Gradle, AGP, the network and
 * the filesystem — the only file it reads is the font it is handed.
 */
internal class DefaultBannerGenerator : BannerGenerator {

    override fun generate(request: BannerRequest): GenerationResult = try {
        Session(request).run()
    } catch (e: GeneratorFailure) {
        GenerationResult.Failure(e.message.orEmpty())
    } catch (e: XmlParseException) {
        GenerationResult.Failure(e.message.orEmpty())
    }

    companion object {
        /**
         * Used when a monochrome banner cannot reuse the original name. Namespaced because the
         * generator refuses to overwrite an existing resource called this.
         */
        const val MONOCHROME_SUFFIX: String = "_iconbanner_mono"
    }
}

/** The role, in a collision message, of a file that backs `icon` or `roundIcon` directly. */
private const val ICON_ROLE = "the launcher icon itself"

private class Session(private val request: BannerRequest) {

    /** Sorted, so output order does not depend on [io.github.bleeding182.iconbanner.api.ResourceLookup] iteration order. */
    private val outputs = sortedMapOf<String, GeneratedFile>()

    /** Adaptive layers already bannered, so a drawable shared by icon and roundIcon is done once. */
    private val bannered = mutableSetOf<Triple<ResourceRef, Mode, String?>>()
    private val info = mutableListOf<String>()

    /** Overlapping corners and skipped files. Insertion-ordered, so the log reads in icon order. */
    private val warnings = linkedSetOf<String>()

    /**
     * One painter per banner, in paint order — the order [BannerRequest.layers] is already in.
     *
     * Built on construction, so the fonts are checked before any icon: a text/font mismatch is the
     * user's own configuration, and easier to act on.
     */
    private val painters: List<BannerPainter> = run {
        // Banners routinely share a face, and parsing a TrueType file is not free.
        val faces = mutableMapOf<java.io.File, BannerText>()
        request.layers.map { layer ->
            val font = faces.getOrPut(layer.fontFile) {
                try {
                    BannerText(layer.fontFile)
                } catch (e: Exception) {
                    fail("Could not read the banner font ${layer.fontFile}: ${e.message}")
                }
            }
            font.firstUndisplayableCharacter(layer.style.text)?.let { character ->
                fail(
                    "The banner font ${layer.fontFile.name} has no glyph for $character in the banner " +
                        "text \"${layer.style.text}\". Choose a font that covers it, or change the text."
                )
            }
            BannerPainter(layer.style, font)
        }
    }

    fun run(): GenerationResult {
        warnAboutSharedCorners()
        val icons = listOfNotNull(request.icon, request.roundIcon).distinct()
        icons.forEach(::processIcon)
        return GenerationResult.Success(
            files = outputs.toMap(),
            info = info.toList(),
            warnings = warnings.toList(),
        )
    }

    /**
     * A warning rather than a failure: `z` exists so a user can say which overlapping banner wins.
     *
     * Only the *same* corner. Adjacent corners cross too, but near the middle of the icon, and two
     * corners are a layout somebody chose.
     */
    private fun warnAboutSharedCorners() {
        request.layers.groupBy { it.style.corner }
            .filterValues { it.size > 1 }
            .forEach { (corner, sharing) ->
                val names = sharing.joinToString(", ") { "\"${it.style.name}\"" }
                warnings += "Banners $names share the $corner corner and will overlap. They are " +
                    "painted in that order, so \"${sharing.first().style.name}\" may end up hidden " +
                    "under the others. Move one to another corner, or set iconBanner z to choose " +
                    "which is on top."
            }
    }

    private fun processIcon(ref: ResourceRef) {
        eachSource(ref, subject = "Launcher icon $ref") { source ->
            when (val content = source.content) {
                is SourceContent.Xml -> bannerIconXml(ref, source, content.text)
                // A bitmap backing the icon itself is a legacy launcher icon. No monochrome pass — such
                // an icon has no monochrome layer.
                is SourceContent.Raster -> bannerRaster(
                    content.bytes, source, describedAs = source.relativePath, Mode.STANDALONE_ICON, ICON_ROLE,
                )
            }
        }
    }

    /** Always null, in [eachSource]'s terms: an XML icon is either bannered or a failure. */
    private fun bannerIconXml(ref: ResourceRef, source: SourceResource, xml: String): String? {
        val document = AndroidXml.parse(xml, source.relativePath)
        when (val root = document.documentElement.localNameOrTag()) {
            "adaptive-icon" -> processAdaptiveIcon(source, document)
            // No adaptive icon: banner the vector directly. Nowhere to declare a monochrome layer.
            "vector" -> bannerVector(document, source, describedAs = source.relativePath, Mode.STANDALONE_ICON, ICON_ROLE)
            else -> fail(
                "Launcher icon $ref (${source.relativePath}) has a <$root> root. The banner " +
                    "generator can only handle <adaptive-icon> and <vector>."
            )
        }
        return null
    }

    /** Foreground gets the coloured banner, monochrome the clip-and-punch. Background is untouched. */
    private fun processAdaptiveIcon(source: SourceResource, document: Document) {
        val root = document.documentElement
        val path = source.relativePath

        val foreground = root.firstChild("foreground")
            ?: fail("$path has no <foreground>, so there is nothing to put a banner on.")
        val foregroundRef = foreground.drawableRef(path, "foreground")
            ?: fail(
                "$path: <foreground> has no android:drawable. A foreground defined inline is not " +
                    "supported; point it at a vector or bitmap drawable instead."
            )
        bannerAdaptiveLayer(foregroundRef, Mode.ADAPTIVE_FOREGROUND, referencedBy = path)

        // No monochrome layer is a normal, supported shape of icon. Skip it without a word.
        val monochrome = root.firstChild("monochrome") ?: return
        val monochromeRef = monochrome.drawableRef(path, "monochrome") ?: return

        if (monochromeRef != foregroundRef) {
            bannerAdaptiveLayer(monochromeRef, Mode.MONOCHROME, referencedBy = path)
            return
        }

        // Foreground and monochrome share a drawable (the Studio template), and one resource name
        // cannot hold both versions. The monochrome copy gets a reserved name.
        val reserved = ResourceRef(
            monochromeRef.type,
            monochromeRef.name + DefaultBannerGenerator.MONOCHROME_SUFFIX,
        )
        if (request.resources.find(reserved).isNotEmpty()) {
            fail(
                "$path: the monochrome banner needs the reserved name $reserved, but that resource " +
                    "already exists. Rename it so the generated monochrome icon can use it."
            )
        }
        bannerAdaptiveLayer(monochromeRef, Mode.MONOCHROME, referencedBy = path, renameTo = reserved.name)

        monochrome.setAndroidAttribute(AndroidXml.androidPrefix(root), "drawable", reserved.toString())
        if (emitXml(path, AndroidXml.serialize(document), ICON_ROLE)) {
            info += "$path replaced: <monochrome> redirected to $reserved"
        }
    }

    /**
     * What a drawable is painted as. The two coloured modes differ only on a bitmap, and only in the
     * silhouette clip — see BannerPainter.paintColored: a standalone icon is drawn unmasked, so its
     * band is clipped to the icon's own alpha rather than running out to the canvas corner, while an
     * adaptive foreground is masked by the system, as the monochrome layer is.
     */
    private enum class Mode { STANDALONE_ICON, ADAPTIVE_FOREGROUND, MONOCHROME }

    /**
     * Every banner onto one document, serialised once.
     *
     * The monochrome phases interleave: each clip wraps whatever is at the root, so all of them must
     * be in place before the first ribbon is appended.
     */
    private fun paint(document: Document, describedAs: String, mode: Mode): String {
        when (mode) {
            Mode.STANDALONE_ICON, Mode.ADAPTIVE_FOREGROUND ->
                painters.forEach { it.paintColored(document, describedAs) }
            Mode.MONOCHROME -> {
                painters.forEach { it.clipMonochrome(document, describedAs) }
                painters.forEach { it.punchMonochrome(document, describedAs) }
            }
        }
        return AndroidXml.serialize(document)
    }

    /**
     * The same two phases over pixels, encoded once by the caller.
     *
     * The monochrome phases stay separate here for a different reason than the vector's: with two
     * banners, the second clear would eat the first fill wherever the bands overlap.
     */
    private fun paint(image: BufferedImage, describedAs: String, mode: Mode) {
        when (mode) {
            Mode.STANDALONE_ICON, Mode.ADAPTIVE_FOREGROUND -> painters.forEach {
                it.paintColored(image, describedAs, clipToSilhouette = mode == Mode.STANDALONE_ICON)
            }
            Mode.MONOCHROME -> {
                painters.forEach { it.clipMonochrome(image, describedAs) }
                painters.forEach { it.punchMonochrome(image, describedAs) }
            }
        }
    }

    /** The drawable an `<adaptive-icon>` layer points at, in every form it may be backed by. */
    private fun bannerAdaptiveLayer(
        ref: ResourceRef,
        mode: Mode,
        referencedBy: String,
        renameTo: String? = null,
    ) {
        // icon and roundIcon routinely point at one adaptive icon's layers, so this runs twice for the
        // same drawable. [emit] would drop the duplicate, but only after every density has been decoded,
        // painted and re-encoded to prove it identical. Keyed on what decides the output rather than on
        // the paths it produces, so two *different* layers landing on one path are still [emit]'s catch.
        if (!bannered.add(Triple(ref, mode, renameTo))) return
        val layer = if (mode == Mode.MONOCHROME) "monochrome" else "foreground"
        val role = "the <$layer> of $referencedBy"
        eachSource(ref, subject = "$referencedBy references $ref, which") { source ->
            val describedAs = "$ref (${source.relativePath})"
            when (val content = source.content) {
                is SourceContent.Xml -> {
                    val document = AndroidXml.parse(content.text, source.relativePath)
                    val root = document.documentElement.localNameOrTag()
                    if (root != "vector") {
                        fail(
                            "$referencedBy references $ref, but ${source.relativePath} has a <$root> " +
                                "root. A banner can only be added to a <vector> or a bitmap."
                        )
                    }
                    bannerVector(document, source, describedAs, mode, role, renameTo)
                }
                is SourceContent.Raster -> bannerRaster(content.bytes, source, describedAs, mode, role, renameTo)
            }
        }
    }

    /**
     * The bannered vector, at its own path or — for a monochrome copy that cannot reuse the name — at
     * [renameTo] in the same folder. The caller has already checked the root is a `<vector>`.
     *
     * Always null, in [eachSource]'s terms: a vector is either bannered or a failure.
     */
    private fun bannerVector(
        document: Document,
        source: SourceResource,
        describedAs: String,
        mode: Mode,
        role: String,
        renameTo: String? = null,
    ): String? {
        val painted = paint(document, describedAs, mode)
        if (renameTo == null) {
            if (emitXml(source.relativePath, painted, role)) info += "${source.relativePath} replaced by a bannered copy"
        } else {
            val path = "${source.qualifiers}/$renameTo.xml"
            if (emitXml(path, painted, role)) info += "$path generated for the monochrome banner"
        }
        return null
    }

    /**
     * The bannered bitmap, always as PNG: the JDK ships no WebP writer, so `ic_launcher.webp` comes
     * out as `ic_launcher.png`. Same resource, same qualifier folder, different extension — the
     * merger keys on name, type and qualifiers, so that is still a clean override and the original is
     * not even compiled.
     *
     * Null when the bitmap was bannered, otherwise why it was skipped. Nothing here fails the build:
     * a file the plugin cannot read is one file, and [eachSource] is what notices when it was the
     * only one.
     */
    private fun bannerRaster(
        bytes: ByteArray,
        source: SourceResource,
        describedAs: String,
        mode: Mode,
        role: String,
        renameTo: String? = null,
    ): String? {
        // A guard rather than a feature: the marker border is part of the file, so compositing over it
        // would corrupt the resource.
        if (source.isNinePatch) return skip(source, "it is a nine-patch")
        val image = decode(bytes, source.relativePath)
            ?: return skip(source, "no image reader could decode it")

        paint(image, describedAs, mode)
        val name = renameTo ?: source.fileName.substringBefore('.')
        val path = "${source.qualifiers}/$name.png"
        if (emit(path, GeneratedFile.Binary(RasterIcon.encode(image)), role)) {
            info += when {
                renameTo != null -> "$path generated for the monochrome banner"
                // Both paths: a webp source comes out as a png, and nothing else would say so.
                path != source.relativePath -> "${source.relativePath} replaced by a bannered copy at $path"
                else -> "$path replaced by a bannered copy"
            }
        }
        return null
    }

    /**
     * Left as it is, and said so. A cosmetic marker missing from one density is not worth failing a
     * build over — but see [eachSource], which does fail when *every* file was skipped.
     *
     * @return [reason], so a caller can tally it.
     */
    private fun skip(source: SourceResource, reason: String): String {
        warnings += "${source.relativePath} is left without a banner: $reason."
        return reason
    }

    /**
     * Every file backing [ref], sorted by path, each handed to [banner] — which returns null when it
     * produced something, or why it did not.
     *
     * Two failures, one rule from the spec: a variant that asked for a marking must never silently
     * get none. Nothing found at all is the first; every single file skipped is the second, and it
     * repeats the reasons because a [io.github.bleeding182.iconbanner.api.GenerationResult.Failure]
     * carries no warnings of its own.
     */
    private fun eachSource(ref: ResourceRef, subject: String, banner: (SourceResource) -> String?) {
        val sources = request.resources.find(ref).sortedBy { it.relativePath }
        if (sources.isEmpty()) fail("$subject was not found in the app's resources.")
        // mapNotNull, not any: every qualifier variant gets its own bannered copy.
        val skipped = sources.mapNotNull { source ->
            banner(source)?.let { reason -> "${source.relativePath} ($reason)" }
        }
        if (skipped.size == sources.size) {
            fail(
                "$subject has no file a banner could be added to: ${skipped.joinToString(", ")}. " +
                    "Check that those files are valid images, or add a vector drawable for this icon."
            )
        }
    }

    /**
     * The JDK's own readers first, the extra ones only when those come up empty.
     *
     * That order and not the reverse, because the extra readers cost a dependency resolution: a project
     * whose legacy icons are PNG would otherwise fetch a WebP reader it has no use for, and *fail* where
     * that resolution cannot reach a repository. Decoding a 192px icon twice costs nothing beside it.
     */
    private fun decode(bytes: ByteArray, resourcePath: String): BufferedImage? {
        RasterIcon.decode(bytes)?.let { return it }
        // False means an earlier file already registered them, so a second attempt would repeat the first.
        if (!ensureImageReaders(resourcePath)) return null
        return RasterIcon.decode(bytes)
    }

    /**
     * Asked for once, and only once the JDK has failed on a bitmap: the Gradle layer resolves its reader
     * from a `Configuration`, and a project that never needs one must not pay for that.
     *
     * @return whether this call is what registered them, which is also whether a failed decode is worth
     * repeating.
     */
    private fun ensureImageReaders(resourcePath: String): Boolean {
        if (imageReadersReady) return false
        imageReadersReady = true
        request.codecs.ensureReadersAvailable(resourcePath)
        return true
    }

    private var imageReadersReady = false

    private fun emitXml(path: String, xml: String, role: String): Boolean = emit(path, GeneratedFile.Text(xml), role)

    /** What each output path was produced as, so a collision can name both sides. Keyed like [outputs]. */
    private val roles = mutableMapOf<String, String>()

    /**
     * Records [file] at [path], produced as [role]. Returns false when this path was already produced.
     *
     * The same output twice is routine — icon and roundIcon share a foreground — and dropped. Different
     * output means one drawable is reached in two roles that banner it differently, say one icon's
     * `<monochrome>` and another's `<foreground>`. Same-name override gives a resource one version, so
     * that cannot be generated; the user's fix is a drawable per role.
     */
    private fun emit(path: String, file: GeneratedFile, role: String): Boolean {
        val existing = outputs[path]
        if (existing != null) {
            if (!existing.sameContentAs(file)) {
                fail(
                    "$path is needed as two different banners: ${roles.getValue(path)} and $role. One " +
                        "resource cannot hold both — give each role its own drawable."
                )
            }
            return false
        }
        outputs[path] = file
        roles[path] = role
        return true
    }

    /** Spelled out rather than `==`: [GeneratedFile.Binary] has no `equals`. */
    private fun GeneratedFile.sameContentAs(other: GeneratedFile): Boolean = when {
        this is GeneratedFile.Text && other is GeneratedFile.Text -> content == other.content
        this is GeneratedFile.Binary && other is GeneratedFile.Binary -> bytes contentEquals other.bytes
        else -> false
    }

    private fun Element.drawableRef(path: String, layer: String): ResourceRef? {
        val raw = androidAttribute("drawable") ?: return null
        if (ResourceRef.isFramework(raw)) {
            fail(
                "$path: <$layer android:drawable=\"$raw\"> is a framework resource; the plugin can only " +
                    "banner the app's own resources. Point it at a drawable in this module instead."
            )
        }
        ResourceRef.withoutPackage(raw)?.let { unqualified ->
            fail(
                "$path: <$layer android:drawable=\"$raw\"> names a package; package-qualified " +
                    "references are not supported, so write it as $unqualified."
            )
        }
        if (ResourceRef.isThemeAttribute(raw)) {
            fail(
                "$path: <$layer android:drawable=\"$raw\"> is a theme attribute, which the plugin " +
                    "cannot resolve: it reads resources, not themes. Instead, point android:drawable at " +
                    "the drawable or mipmap directly."
            )
        }
        return ResourceRef.parse(raw)
            ?: fail("$path: <$layer android:drawable=\"$raw\"> is not a resource reference.")
    }
}
