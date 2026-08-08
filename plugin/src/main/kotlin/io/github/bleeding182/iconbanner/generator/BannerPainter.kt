package io.github.bleeding182.iconbanner.generator

import io.github.bleeding182.iconbanner.api.BannerStyle
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.util.Locale

/**
 * Signals a condition the user has to fix. Caught at the top of the generator and returned as
 * [io.github.bleeding182.iconbanner.api.GenerationResult.Failure].
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
    /** Collects legibility complaints. A set, so repeating the same one per icon file is harmless. */
    private val warnings: MutableSet<String> = linkedSetOf(),
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
        fittedOutline(ribbon)?.let { outline ->
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
            fittedOutline(ribbon),
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

    /**
     * The fitted text's `pathData`, warning first if the auto-fit had to shrink it past readability.
     *
     * The fit has no floor — it will happily squeeze eleven characters into a band sized for three —
     * and the result is a wedge of colour with an unreadable smear in it. That is not worth failing a
     * build over, because only the user can say whether their text is worth the size, but it is
     * worth saying out loud: the banner exists to be read.
     */
    private fun fittedOutline(ribbon: Ribbon): String? {
        val fitted = text.fit(style.text, ribbon) ?: return null
        val onScreenDp = fitted.capHeight / ribbon.s * LAUNCHER_DP_PER_EDGE
        if (onScreenDp < MIN_LEGIBLE_CAP_HEIGHT_DP) {
            warnings += String.format(
                Locale.ROOT,
                "The banner text \"%s\" (%d characters) had to be shrunk to a cap height of %.2f in a " +
                    "%.0f viewport to fit the ribbon — roughly %.1fdp on a launcher icon, which is " +
                    "too small to read. Use shorter text, or a larger iconBanner.height.",
                style.text,
                style.text.length,
                fitted.capHeight,
                ribbon.s,
                onScreenDp,
            )
        }
        return fitted.pathData
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

        /**
         * On-screen dp the shorter viewport edge covers, so a fitted size in viewport units can be
         * judged at the size a user actually sees.
         *
         * A launcher scales an adaptive icon so its 72dp mask fills the icon slot, and that slot is
         * 48dp on a stock launcher. The full 108dp canvas therefore lands at `108 * 48/72 = 72`dp.
         */
        const val LAUNCHER_DP_PER_EDGE: Double = 72.0

        /**
         * Cap height below which the fitted text is not worth calling text, in on-screen dp.
         *
         * 4dp is about 12 physical pixels at xxhdpi and 8 at xhdpi — the floor at which uppercase
         * glyphs are still distinguishable from a smear. It is deliberately well under anything
         * anyone would choose on purpose: at the default height of 20 it clears `DEBUG` (6.7dp) and
         * `STAGING` (4.7dp) and catches `STAGING RC1` (3.0dp), which is the case that prompted it.
         */
        const val MIN_LEGIBLE_CAP_HEIGHT_DP: Double = 4.0
    }
}
