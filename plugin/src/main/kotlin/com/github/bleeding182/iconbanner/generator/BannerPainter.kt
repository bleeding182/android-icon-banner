package com.github.bleeding182.iconbanner.generator

import com.github.bleeding182.iconbanner.api.BannerStyle
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Signals a condition the user has to fix. Caught at the top of the generator and returned as
 * [com.github.bleeding182.iconbanner.api.GenerationResult.Failure].
 */
internal class GeneratorFailure(message: String) : Exception(message)

internal fun fail(message: String): Nothing = throw GeneratorFailure(message)

/**
 * Applies a banner to a parsed `<vector>` document, in the two forms the plugin needs.
 *
 * Instances are single-use per document: both methods mutate the [Document] they are given.
 */
internal class BannerPainter(
    private val style: BannerStyle,
    private val text: BannerText,
) {

    /**
     * Two `<path>` elements appended to the vector's root: the ribbon, then the text on top.
     *
     * Appending at root level rather than inserting anywhere clever means the banner draws above
     * everything and is unaffected by whatever groups, transforms or clip-paths the original vector
     * already contains.
     */
    fun colored(document: Document, describedAs: String): String {
        val root = document.documentElement
        val ribbon = ribbonFor(root, describedAs)
        val prefix = AndroidXml.androidPrefix(root)

        root.appendChild(
            document.createVectorElement("path").apply {
                setAndroidAttribute(prefix, "pathData", ribbon.quadPathData())
                setAndroidAttribute(prefix, "fillColor", style.color)
            }
        )
        text.outlinePathData(style.text, ribbon)?.let { outline ->
            root.appendChild(
                document.createVectorElement("path").apply {
                    setAndroidAttribute(prefix, "pathData", outline)
                    setAndroidAttribute(prefix, "fillColor", style.textColor)
                }
            )
        }
        return AndroidXml.serialize(document)
    }

    /**
     * Clip-and-punch: the icon's own content moves into a `<group>` clipped to everything *outside*
     * the ribbon, and the ribbon plus text becomes a single even-odd path.
     *
     * A themed icon keeps only alpha. Layering an opaque ribbon with opaque text on top of opaque
     * artwork would therefore render as one solid, unreadable wedge. Clipping stops the artwork
     * bleeding into the band, and even-odd turns the glyphs into transparent holes so the text
     * reads as the icon's background colour.
     */
    fun monochrome(document: Document, describedAs: String): String {
        val root = document.documentElement
        val ribbon = ribbonFor(root, describedAs)
        val prefix = AndroidXml.androidPrefix(root)

        val group = document.createVectorElement("group")
        group.appendChild(
            document.createVectorElement("clip-path").apply {
                setAndroidAttribute(prefix, "pathData", ribbon.inverseClipPathData())
            }
        )
        // Snapshot before moving: appendChild detaches from the old parent, which would otherwise
        // renumber the live NodeList underneath us.
        val original = (0 until root.childNodes.length).map { root.childNodes.item(it) }
        original.forEach(group::appendChild)
        root.appendChild(group)

        val ribbonAndText = listOfNotNull(
            ribbon.quadPathData(),
            text.outlinePathData(style.text, ribbon),
        ).joinToString(" ")

        root.appendChild(
            document.createVectorElement("path").apply {
                setAndroidAttribute(prefix, "pathData", ribbonAndText)
                setAndroidAttribute(prefix, "fillType", "evenOdd")
                setAndroidAttribute(prefix, "fillColor", MONOCHROME_FILL)
            }
        )
        return AndroidXml.serialize(document)
    }

    private fun ribbonFor(root: Element, describedAs: String): Ribbon {
        val width = root.viewport("viewportWidth", describedAs)
        val height = root.viewport("viewportHeight", describedAs)
        return Ribbon(width, height, style.corner, style.heightPercent)
    }

    private fun Element.viewport(attribute: String, describedAs: String): Double {
        val raw = androidAttribute(attribute)
            ?: fail("$describedAs: <vector> has no android:$attribute, so the banner cannot be sized.")
        val value = raw.toDoubleOrNull()
            ?: fail("$describedAs: android:$attribute is \"$raw\", which is not a number.")
        if (value <= 0.0) fail("$describedAs: android:$attribute is $raw, which must be positive.")
        return value
    }

    internal companion object {
        /**
         * Monochrome layers are tinted by the system and only their alpha survives, so the literal
         * colour is irrelevant as long as it is fully opaque.
         */
        const val MONOCHROME_FILL: String = "#FFFFFFFF"
    }
}
