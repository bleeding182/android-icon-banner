package io.github.bleeding182.iconbanner.generator

import io.github.bleeding182.iconbanner.api.BannerGenerator
import io.github.bleeding182.iconbanner.api.BannerRequest
import io.github.bleeding182.iconbanner.api.GenerationResult
import io.github.bleeding182.iconbanner.api.ResourceRef
import io.github.bleeding182.iconbanner.api.SourceResource
import org.w3c.dom.Document
import org.w3c.dom.Element

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

private class Session(private val request: BannerRequest) {

    /** Sorted, so output order does not depend on [io.github.bleeding182.iconbanner.api.ResourceLookup] iteration order. */
    private val outputs = sortedMapOf<String, String>()
    private val info = mutableListOf<String>()

    /** A set: the same text is fitted once per qualifier variant, and one complaint is plenty. */
    private val warnings = linkedSetOf<String>()

    /** One painter per banner, in paint order — the order [BannerRequest.layers] is already in. */
    private val painters: List<BannerPainter> by lazy {
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
            BannerPainter(layer.style, font, warnings, nameWarnings = request.layers.size > 1)
        }
    }

    fun run(): GenerationResult {
        // Fonts first: a text/font mismatch is the user's own configuration, and easier to act on.
        painters
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
        for (source in xmlSourcesOf(ref, subject = "Launcher icon $ref")) {
            val document = AndroidXml.parse(source.xml!!, source.relativePath)
            when (val root = document.documentElement.localNameOrTag()) {
                "adaptive-icon" -> processAdaptiveIcon(source, document)
                // No adaptive icon: banner the vector directly. Nowhere to declare a monochrome layer.
                "vector" -> if (emit(source.relativePath, paint(document, source.relativePath, Mode.COLORED))) {
                    info += "${source.relativePath} replaced by a bannered copy"
                }
                else -> fail(
                    "Launcher icon $ref (${source.relativePath}) has a <$root> root. The banner " +
                        "generator can only handle <adaptive-icon> and <vector>."
                )
            }
        }
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
                    "supported; point it at a vector drawable instead."
            )
        bannerVector(foregroundRef, Mode.COLORED, referencedBy = path)

        // No monochrome layer is a normal, supported shape of icon. Skip it without a word.
        val monochrome = root.firstChild("monochrome") ?: return
        val monochromeRef = monochrome.drawableRef(path, "monochrome") ?: return

        if (monochromeRef != foregroundRef) {
            bannerVector(monochromeRef, Mode.MONOCHROME, referencedBy = path)
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
        bannerVector(monochromeRef, Mode.MONOCHROME, referencedBy = path, renameTo = reserved.name)

        monochrome.setAndroidAttribute(AndroidXml.androidPrefix(root), "drawable", reserved.toString())
        if (emit(path, AndroidXml.serialize(document))) {
            info += "$path replaced: <monochrome> redirected to $reserved"
        }
    }

    private enum class Mode { COLORED, MONOCHROME }

    /**
     * Every banner onto one document, serialised once.
     *
     * The monochrome phases interleave: each clip wraps whatever is at the root, so all of them must
     * be in place before the first ribbon is appended.
     */
    private fun paint(document: Document, describedAs: String, mode: Mode): String {
        when (mode) {
            Mode.COLORED -> painters.forEach { it.paintColored(document, describedAs) }
            Mode.MONOCHROME -> {
                painters.forEach { it.clipMonochrome(document, describedAs) }
                painters.forEach { it.punchMonochrome(document, describedAs) }
            }
        }
        return AndroidXml.serialize(document)
    }

    private fun bannerVector(
        ref: ResourceRef,
        mode: Mode,
        referencedBy: String,
        renameTo: String? = null,
    ) {
        val subject = "$referencedBy references $ref, which"
        for (source in xmlSourcesOf(ref, subject = subject)) {
            val describedAs = "$ref (${source.relativePath})"
            val document = AndroidXml.parse(source.xml!!, source.relativePath)
            val root = document.documentElement.localNameOrTag()
            if (root != "vector") {
                fail(
                    "$referencedBy references $ref, but ${source.relativePath} has a <$root> root. " +
                        "A banner can only be added to a <vector>."
                )
            }
            val content = paint(document, describedAs, mode)
            if (renameTo == null) {
                if (emit(source.relativePath, content)) {
                    info += "${source.relativePath} replaced by a bannered copy"
                }
            } else {
                val path = "${source.qualifiers}/$renameTo.xml"
                if (emit(path, content)) {
                    info += "$path generated for the monochrome banner"
                }
            }
        }
    }

    /**
     * Every XML file backing [ref], sorted by path.
     *
     * Rasters are dropped silently. *No* XML fails instead: the variant asked for a marking and
     * would otherwise silently get none.
     */
    private fun xmlSourcesOf(ref: ResourceRef, subject: String): List<SourceResource> {
        val all = request.resources.find(ref)
        if (all.isEmpty()) fail("$subject was not found in the app's resources.")
        val xml = all.filter { it.xml != null }.sortedBy { it.relativePath }
        if (xml.isEmpty()) {
            val found = all.map { it.relativePath }.sorted().joinToString(", ")
            fail(
                "$subject has no XML to add a banner to; only raster files were found ($found). " +
                    "Add an adaptive icon or a vector drawable for this icon."
            )
        }
        return xml
    }

    /** Records [content] at [path]. Returns false when this path was already produced. */
    private fun emit(path: String, content: String): Boolean {
        val existing = outputs[path]
        if (existing != null) {
            // icon and roundIcon routinely share a foreground; differing content would be a bug.
            if (existing != content) {
                fail("Internal error: two different banner results were produced for $path.")
            }
            return false
        }
        outputs[path] = content
        return true
    }

    private fun Element.drawableRef(path: String, layer: String): ResourceRef? {
        val raw = androidAttribute("drawable") ?: return null
        return ResourceRef.parse(raw)
            ?: fail("$path: <$layer android:drawable=\"$raw\"> is not a resource reference.")
    }
}
