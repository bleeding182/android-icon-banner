package com.github.bleeding182.iconbanner.generator

import com.github.bleeding182.iconbanner.api.BannerGenerator
import com.github.bleeding182.iconbanner.api.BannerRequest
import com.github.bleeding182.iconbanner.api.GenerationResult
import com.github.bleeding182.iconbanner.api.ResourceRef
import com.github.bleeding182.iconbanner.api.SourceResource
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * The pure seam: launcher icon references in, resource file contents out.
 *
 * Deliberately free of Gradle, AGP, the network and the filesystem — the only file it reads is the
 * TrueType font it is handed. Everything the plugin can get wrong about geometry, XML rewriting and
 * number formatting is reachable from here with a plain unit test.
 */
class DefaultBannerGenerator : BannerGenerator {

    override fun generate(request: BannerRequest): GenerationResult = try {
        Session(request).run()
    } catch (e: GeneratorFailure) {
        GenerationResult.Failure(e.message.orEmpty())
    } catch (e: XmlParseException) {
        GenerationResult.Failure(e.message.orEmpty())
    }

    companion object {
        /**
         * Appended to a drawable's name when its monochrome banner cannot reuse the original name.
         *
         * Plugin-namespaced on purpose: the generator refuses to overwrite an existing resource
         * with this name, and a distinctive suffix makes that collision essentially impossible to
         * hit by accident.
         */
        const val MONOCHROME_SUFFIX: String = "_iconbanner_mono"
    }
}

private class Session(private val request: BannerRequest) {

    private val style = request.style

    /** Sorted, so output order does not depend on [com.github.bleeding182.iconbanner.api.ResourceLookup] iteration order. */
    private val outputs = sortedMapOf<String, String>()
    private val info = mutableListOf<String>()

    private val painter: BannerPainter by lazy {
        val font = try {
            BannerText(request.fontFile)
        } catch (e: Exception) {
            fail("Could not read the banner font ${request.fontFile}: ${e.message}")
        }
        font.firstUndisplayableCharacter(style.text)?.let { character ->
            fail(
                "The banner font ${request.fontFile.name} has no glyph for $character in the banner " +
                    "text \"${style.text}\". Choose a font that covers it, or change the text."
            )
        }
        BannerPainter(style, font)
    }

    fun run(): GenerationResult {
        // Force the font up front, so a text/font mismatch is reported before anything about the
        // project's resources. It is the user's own configuration and the easier thing to act on.
        painter
        val icons = listOfNotNull(request.icon, request.roundIcon).distinct()
        icons.forEach(::processIcon)
        return GenerationResult.Success(files = outputs.toMap(), info = info.toList())
    }

    private fun processIcon(ref: ResourceRef) {
        for (source in xmlSourcesOf(ref, subject = "Launcher icon $ref")) {
            val document = AndroidXml.parse(source.xml!!, source.relativePath)
            when (val root = document.documentElement.localNameOrTag()) {
                "adaptive-icon" -> processAdaptiveIcon(source, document)
                // An app that never migrated to adaptive icons: banner the vector directly. There
                // is no monochrome layer to produce, because there is nowhere to declare one.
                "vector" -> if (emit(source.relativePath, painter.colored(document, source.relativePath))) {
                    info += "${source.relativePath} replaced by a bannered copy"
                }
                else -> fail(
                    "Launcher icon $ref (${source.relativePath}) has a <$root> root. The banner " +
                        "generator can only handle <adaptive-icon> and <vector>."
                )
            }
        }
    }

    /**
     * Foreground gets the coloured banner; monochrome gets the clip-and-punch treatment. Background
     * is read past and never touched — the ribbon belongs on the artwork, not behind it.
     */
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

        // Foreground and monochrome share one drawable — the default Android Studio template. One
        // resource name cannot hold both the coloured and the punched-out version, so the
        // monochrome copy gets a reserved name and the adaptive icon is redirected to it.
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
            val content = when (mode) {
                Mode.COLORED -> painter.colored(document, describedAs)
                Mode.MONOCHROME -> painter.monochrome(document, describedAs)
            }
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
     * Raster variants are dropped without comment — bannering them is explicitly out of scope, and
     * a per-build warning about a shelved feature is just noise. A resource with *no* XML at all is
     * a different matter: the variant asked for a marking and would silently get none, which is the
     * exact failure this plugin exists to prevent.
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
            // icon and roundIcon routinely share a foreground, so re-deriving identical content is
            // expected. Genuinely different content for one path would be a generator bug.
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
