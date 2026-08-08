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
 * Applies one banner to a parsed `<vector>` document, in the two forms the plugin needs.
 *
 * Every method mutates the [Document] and none of them serialises, so a caller can run several
 * painters over one document and write it out once.
 */
internal class BannerPainter(
    private val style: BannerStyle,
    private val text: BannerText,
    /**
     * Legibility complaints, shared across the request. A set, so the same complaint raised for each
     * qualifier variant of an icon folds into one line.
     */
    private val warnings: MutableSet<String> = linkedSetOf(),
    /** Whether a complaint names its banner. Only set when the request carries more than one. */
    private val nameWarnings: Boolean = false,
) {

    /**
     * The ribbon, then the text on top, appended at the root — above everything, and unaffected by
     * the original vector's groups and transforms. Painters stack in call order.
     */
    fun paintColored(document: Document, describedAs: String) {
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
    }

    /**
     * Clip-and-punch, first half: everything at the root moves into a `<group>` clipped to everything
     * *outside* this ribbon, so opaque artwork cannot bleed into the band of a themed icon.
     *
     * It wraps whatever is at the root, which is what makes several banners work: `VectorDrawable`
     * **unions** two `<clip-path>` elements in one `<group>` rather than intersecting them, so the
     * groups have to nest.
     */
    fun clipMonochrome(document: Document, describedAs: String) {
        val root = document.documentElement
        val ribbon = ribbonFor(root, describedAs)
        val prefix = AndroidXml.androidPrefix(root)

        val group = document.createVectorElement("group")
        group.appendChild(
            document.createVectorElement("clip-path").apply {
                setAndroidAttribute(prefix, "pathData", ribbon.inverseClipPathData())
            }
        )
        // Snapshot first: appendChild detaches, renumbering the live NodeList.
        val original = (0 until root.childNodes.length).map { root.childNodes.item(it) }
        original.forEach(group::appendChild)
        root.appendChild(group)
    }

    /**
     * Second half: the ribbon and its text as one even-odd `<path>` at the root.
     *
     * Must run after *every* banner's [clipMonochrome], or the next group swallows this ribbon.
     */
    fun punchMonochrome(document: Document, describedAs: String) {
        val root = document.documentElement
        val ribbon = ribbonFor(root, describedAs)
        val prefix = AndroidXml.androidPrefix(root)

        val ribbonAndText = listOfNotNull(
            ribbon.quadPathData(),
            fittedOutline(ribbon),
        ).joinToString(" ")

        root.appendChild(
            document.createVectorElement("path").apply {
                setAndroidAttribute(prefix, "pathData", ribbonAndText)
                setAndroidAttribute(prefix, "fillType", "evenOdd")
                setAndroidAttribute(prefix, "fillColor", monochromeFill(style.monochromeAlphaPercent))
            }
        )
    }

    /** The text's `pathData`, warning first if the fit forced it past readability. */
    private fun fittedOutline(ribbon: Ribbon): String? {
        val fitted = text.fit(style.text, ribbon) ?: return null
        val onScreenDp = fitted.capHeight / ribbon.s * LAUNCHER_DP_PER_EDGE
        if (onScreenDp < MIN_LEGIBLE_CAP_HEIGHT_DP) {
            // Two decimals: %.1f rounds 3.98 to "4.0", reading as though 4dp were under a 4dp minimum.
            // position is only named when it is what ran out — on the default it buys a few percent and
            // costs the middle of the icon.
            val remedy = if (style.positionPercent > Ribbon.DEFAULT_POSITION_PERCENT) {
                String.format(
                    Locale.ROOT,
                    "The ribbon is shortened by the icon's mask and by iconBanner.position (%.0f, " +
                        "against a default of %.0f), so pull the position back in, or use shorter " +
                        "text or a narrower font.",
                    style.positionPercent,
                    Ribbon.DEFAULT_POSITION_PERCENT,
                )
            } else {
                "The ribbon's length is fixed by the icon's mask, so use shorter text or a narrower " +
                    "font."
            }
            warnings += (if (nameWarnings) "${style.name}: " else "") + String.format(
                Locale.ROOT,
                "The banner text \"%s\" (%d characters) had to be shrunk to a cap height of %.2f in a " +
                    "%.0f viewport to fit across the ribbon — about %.2fdp on a launcher icon, under " +
                    "the %.0fdp needed to stay readable. ",
                style.text,
                style.text.length,
                fitted.capHeight,
                ribbon.s,
                onScreenDp,
                MIN_LEGIBLE_CAP_HEIGHT_DP,
            ) + remedy
        }
        return fitted.pathData
    }

    /** Measured once: the band derives from the text, and this does not vary by icon file. */
    private val textWidthPerCapHeight: Double? by lazy { text.naturalWidthPerCapHeight(style.text) }

    private fun ribbonFor(root: Element, describedAs: String): Ribbon {
        val width = root.viewport("viewportWidth", describedAs)
        val height = root.viewport("viewportHeight", describedAs)
        return Ribbon(
            viewportWidth = width,
            viewportHeight = height,
            corner = style.corner,
            positionPercent = style.positionPercent,
            maxTextSizePercent = style.maxTextSizePercent,
            lineHeight = style.lineHeight,
            textWidthPerCapHeight = textWidthPerCapHeight,
        )
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
         * The system tints this layer, replacing the RGB and keeping the alpha, so white is
         * arbitrary and the alpha is the whole of what a banner can choose here.
         */
        fun monochromeFill(alphaPercent: Double): String =
            String.format(Locale.ROOT, "#%02XFFFFFF", Math.round(alphaPercent / 100.0 * 255))

        /** On-screen dp of the shorter viewport edge: a launcher fits the 72dp mask into a 48dp slot. */
        const val LAUNCHER_DP_PER_EDGE: Double = 72.0

        /**
         * Well under anything chosen on purpose: at the default style it clears `STAGING` (5.4dp) and
         * catches `STAGING RC1` (3.6dp).
         */
        const val MIN_LEGIBLE_CAP_HEIGHT_DP: Double = 4.0
    }
}
