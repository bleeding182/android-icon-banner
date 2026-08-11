package io.github.bleeding182.iconbanner.generator

import io.github.bleeding182.iconbanner.api.BannerStyle
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Area
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.util.Locale

/**
 * Signals a condition the user has to fix. Caught at the top of the generator and returned as
 * [io.github.bleeding182.iconbanner.api.GenerationResult.Failure].
 */
internal class GeneratorFailure(message: String) : Exception(message)

internal fun fail(message: String): Nothing = throw GeneratorFailure(message)

internal class BannerPainter(
    private val style: BannerStyle,
    private val text: BannerText,
    /** Legibility complaints, shared across the request so two banners' lines arrive together. */
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
        fittedText(ribbon)?.let { fitted ->
            root.appendChild(
                document.createVectorElement("path").apply {
                    setAndroidAttribute(prefix, "pathData", fitted.pathData)
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
            fittedText(ribbon)?.pathData,
        ).joinToString(" ")

        root.appendChild(
            document.createVectorElement("path").apply {
                setAndroidAttribute(prefix, "pathData", ribbonAndText)
                setAndroidAttribute(prefix, "fillType", "evenOdd")
                setAndroidAttribute(prefix, "fillColor", monochromeFill(style.monochromeAlphaPercent))
            }
        )
    }

    /**
     * The ribbon, then the text on top, composited into the pixels. Painters stack in call order,
     * exactly as the vector's appended `<path>` elements do.
     *
     * @param clipToSilhouette paint only where the icon already has alpha, the raster analogue of the
     * mask an adaptive icon gets for free. True for a **standalone legacy icon** — a launcher draws
     * `mipmap-hdpi/ic_launcher.webp` unmasked, so an unclipped band runs out to the canvas corner and
     * extends a round icon's silhouette with a floating triangle. False for an **adaptive icon's
     * `<foreground>`**: the system masks that layer, and such a foreground is usually a logo on a large
     * *transparent* surround, where clipping would erase nearly the whole band. The caller knows which
     * of the two it has; the pixels cannot say.
     */
    fun paintColored(image: BufferedImage, describedAs: String, clipToSilhouette: Boolean) {
        image.requireAlphaChannel(describedAs)
        val ribbon = ribbonFor(image)
        image.paint {
            if (clipToSilhouette) composite = AlphaComposite.SrcAtop
            color = colorOf(style.color, "color", describedAs)
            fill(ribbon.quad())
            fittedText(ribbon)?.let { fitted ->
                color = colorOf(style.textColor, "textColor", describedAs)
                // After the band, so its own alpha lands on the band and not on the artwork below.
                fill(fitted.glyphs)
            }
        }
    }

    /**
     * Clip-and-punch, first half: the band's pixels are cleared, so artwork cannot bleed into the band
     * of a themed icon, which keeps only alpha and would otherwise render as a solid untextured wedge.
     *
     * No silhouette clip, for the reason the vector has none either — the system masks this layer.
     *
     * Must run for *every* banner before the first [punchMonochrome], or the second banner's clear
     * eats the first banner's fill.
     */
    fun clipMonochrome(image: BufferedImage, describedAs: String) {
        image.requireAlphaChannel(describedAs)
        val ribbon = ribbonFor(image)
        image.paint {
            composite = AlphaComposite.Clear
            fill(ribbon.quad())
        }
    }

    /**
     * Second half: the band filled back in at [BannerStyle.monochromeAlphaPercent], with the glyphs
     * left as holes.
     *
     * `evenOdd` is a fill *rule* and has no raster counterpart, so the region is built by subtraction
     * instead. It comes out the same, glyph counters included: the rule's third crossing fills the
     * inside of a `D`, and so does not subtracting it.
     */
    fun punchMonochrome(image: BufferedImage, describedAs: String) {
        image.requireAlphaChannel(describedAs)
        val ribbon = ribbonFor(image)
        val band = Area(ribbon.quad())
        fittedText(ribbon)?.let { band.subtract(Area(it.glyphs)) }
        image.paint {
            // White is arbitrary and the alpha is everything — see monochromeFill, whose rounding this
            // shares so a themed bitmap and a themed vector come out at the same opacity.
            color = Color(255, 255, 255, monochromeAlphaByte(style.monochromeAlphaPercent))
            fill(band)
        }
    }

    /**
     * A launcher icon is small and a jagged edge on a 45° band is the first thing a reader notices, so
     * antialiasing and pure stroke geometry rather than the defaults.
     */
    private inline fun BufferedImage.paint(block: Graphics2D.() -> Unit) {
        val graphics = createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            graphics.block()
        } finally {
            graphics.dispose()
        }
    }

    /**
     * `Clear` and `SrcAtop` both work through the destination's alpha, and on an image that has none
     * they paint opaque black rather than nothing — silently, and over the whole band.
     * [RasterIcon.decode] exists to rule that out, so reaching this is a wiring mistake.
     */
    private fun BufferedImage.requireAlphaChannel(describedAs: String) {
        if (colorModel.hasAlpha()) return
        fail("Internal error: $describedAs was handed to the banner painter without an alpha channel.")
    }

    /** [Ribbon.quadPathData]'s twin, off the same four points. */
    private fun Ribbon.quad(): Path2D = Path2D.Double().apply {
        moveTo(p1x, p1y)
        lineTo(p2x, p2y)
        lineTo(p3x, p3y)
        lineTo(p4x, p4y)
        closePath()
    }

    private fun colorOf(value: String, property: String, describedAs: String): Color =
        RasterIcon.parseColor(value)
            ?: fail(
                "$describedAs: banner \"${style.name}\" has $property \"$value\", which is not a colour " +
                    "a bitmap icon can be painted with. Use a hex literal such as #FFE91E63."
            )

    /**
     * Whether this banner has complained already. One painter is one banner and outlives every file in
     * the request, so it is the thing that knows.
     *
     * Deduplicating on the message text is not enough once bitmaps are involved: the fit quantises
     * against the pixel grid, so one text comes out 3.59dp at mdpi and 3.60dp at xxxhdpi and the two
     * strings never match. A vector's qualifier variants all declare one viewport, so those were
     * character-identical and this never showed.
     */
    private var complained = false

    /** The fitted glyphs, warning first if the fit forced them past readability. */
    private fun fittedText(ribbon: Ribbon): FittedText? {
        val fitted = text.fit(style.text, ribbon) ?: return null
        val onScreenDp = fitted.capHeight / ribbon.s * LAUNCHER_DP_PER_EDGE
        if (onScreenDp < MIN_LEGIBLE_CAP_HEIGHT_DP && !complained) {
            complained = true
            // No cap height and no viewport in the message: both are the icon's own units, which differ
            // per density on a bitmap. The dp is the whole point. position is only named when it is what
            // ran out — on the default it buys a few percent and costs the middle of the icon.
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
            // Two decimals: %.1f rounds 3.98 to "4.0", reading as though 4dp were under a 4dp minimum.
            warnings += (if (nameWarnings) "${style.name}: " else "") + String.format(
                Locale.ROOT,
                "The banner text \"%s\" (%d characters) had to be shrunk to fit across the ribbon — " +
                    "about %.2fdp on a launcher icon, under the %.0fdp needed to stay readable. ",
                style.text,
                style.text.length,
                onScreenDp,
                MIN_LEGIBLE_CAP_HEIGHT_DP,
            ) + remedy
        }
        return fitted
    }

    /** Measured once: the band derives from the text, and this does not vary by icon file. */
    private val textWidthPerCapHeight: Double? by lazy { text.naturalWidthPerCapHeight(style.text) }

    private fun ribbonFor(root: Element, describedAs: String): Ribbon = ribbon(
        viewportWidth = root.viewport("viewportWidth", describedAs),
        viewportHeight = root.viewport("viewportHeight", describedAs),
    )

    /** A bitmap declares no viewport, so its pixel size is one. Nothing else about the geometry moves. */
    private fun ribbonFor(image: BufferedImage): Ribbon =
        ribbon(image.width.toDouble(), image.height.toDouble())

    private fun ribbon(viewportWidth: Double, viewportHeight: Double): Ribbon = Ribbon(
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        corner = style.corner,
        positionPercent = style.positionPercent,
        maxTextSizePercent = style.maxTextSizePercent,
        lineHeight = style.lineHeight,
        textWidthPerCapHeight = textWidthPerCapHeight,
    )

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
            String.format(Locale.ROOT, "#%02XFFFFFF", monochromeAlphaByte(alphaPercent))

        /** One rounding rule for both icon forms, so the same percentage cannot mean two opacities. */
        fun monochromeAlphaByte(alphaPercent: Double): Int =
            Math.round(alphaPercent / 100.0 * 255).toInt()

        /** On-screen dp of the shorter viewport edge: a launcher fits the 72dp mask into a 48dp slot. */
        const val LAUNCHER_DP_PER_EDGE: Double = 72.0

        /**
         * Well under anything chosen on purpose: at the default style it clears `STAGING` (5.4dp) and
         * catches `STAGING RC1` (3.6dp).
         */
        const val MIN_LEGIBLE_CAP_HEIGHT_DP: Double = 4.0
    }
}
