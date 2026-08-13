package io.github.bleeding182.iconbanner.generator

import io.github.bleeding182.iconbanner.api.BannerCorner
import io.github.bleeding182.iconbanner.api.BannerStyle
import io.github.bleeding182.iconbanner.api.GenerationResult
import io.github.bleeding182.iconbanner.api.ResourceRef
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A request carrying several banners.
 *
 * Kept apart from [BannerGeneratorTestCases] on purpose: that suite is the single-banner contract and
 * runs twice over goldens that must not move, while everything here is about what changes when a
 * second layer arrives — paint order, group nesting, and the corners they may end up sharing.
 */
class MultiBannerGeneratorTest {

    // ------------------------------------------------------------ paint order

    @Test
    fun `two banners in opposite corners produce two ribbons and two glyph paths`() {
        val output = plainVector(main(), sha())
        assertMatchesGolden("multi_opposite_corners.xml", output)

        // Ribbon then text per banner, so a later one paints over an earlier one.
        assertEquals(
            listOf(GREEN, WHITE, MAIN_FILL, MAIN_TEXT_FILL, SHA_FILL, SHA_TEXT_FILL),
            fillColorsOf(output),
        )
        assertEquals(2, glyphPathsOf(output).size, output)
    }

    @Test
    fun `each banner is drawn in its own corner`() {
        // Same text in both, so only the position can differ.
        val output = plainVector(main(text = "DEV"), sha(text = "DEV"))

        val fromTopLeft = pathPoints(quadPathOf(output, MAIN_FILL)).map { (x, y) -> x + y }.sorted()
        val fromBottomRight = pathPoints(quadPathOf(output, SHA_FILL))
            .map { (x, y) -> (108.0 - x) + (108.0 - y) }
            .sorted()

        assertEquals(fromTopLeft.size, fromBottomRight.size, output)
        fromTopLeft.zip(fromBottomRight).forEach { (left, right) ->
            assertEquals(left, right, 1e-9, output)
        }
    }

    @Test
    fun `paint order follows the layer order, not the corner`() {
        // The forwards order is asserted above; declaring them the other way round has to reverse it.
        assertEquals(
            listOf(GREEN, WHITE, SHA_FILL, SHA_TEXT_FILL, MAIN_FILL, MAIN_TEXT_FILL),
            fillColorsOf(plainVector(sha(), main())),
        )
    }

    @Test
    fun `adding a second banner leaves the first one's output untouched`() {
        // Everything is derived per banner, so a second one appends rather than replacing.
        val one = plainVector(main())
        val two = plainVector(main(), sha())

        assertTrue(two.startsWith(one.substringBeforeLast("</vector>")), two)
    }

    // -------------------------------------------------------- monochrome nesting

    @Test
    fun `monochrome wraps each banner in a group of its own, nested`() {
        val monochrome = adaptiveIcon(main(), sha()).xml(MONO_PATH)
        assertMatchesGolden("multi_opposite_corners_monochrome.xml", monochrome)

        val root = AndroidXml.parse(monochrome, MONO_PATH).documentElement

        // Nested, not siblings: two <clip-path> elements in one <group> are unioned, not intersected.
        val outer = root.childElements().single { it.localNameOrTag() == "group" }
        val outerChildren = outer.childElements()
        assertEquals(listOf("clip-path", "group"), outerChildren.map { it.localNameOrTag() })

        // The inner group holds the other clip and the icon's own two paths, untouched.
        val inner = outerChildren[1]
        assertEquals(listOf("clip-path", "path", "path"), inner.childElements().map { it.localNameOrTag() })

        // One clip per banner, and no two of them in the same group.
        assertEquals(2, Regex("<clip-path").findAll(monochrome).count(), monochrome)
    }

    @Test
    fun `both even-odd paths sit at the root, after every group`() {
        val monochrome = adaptiveIcon(main(), sha()).xml(MONO_PATH)
        val root = AndroidXml.parse(monochrome, MONO_PATH).documentElement

        assertEquals(listOf("group", "path", "path"), root.childElements().map { it.localNameOrTag() })
        val punched = root.childElements().drop(1)
        assertTrue(punched.all { it.androidAttribute("fillType") == "evenOdd" }, monochrome)
        assertTrue(
            punched.all { it.androidAttribute("fillColor") == BannerPainter.monochromeFill(100.0) },
            monochrome,
        )

        // A ribbon appended before the next clip is swallowed by that group.
        assertEquals(2, Regex("android:fillType=\"evenOdd\"").findAll(monochrome).count(), monochrome)
    }

    @Test
    fun `each banner's punched path matches its own band`() {
        val result = adaptiveIcon(main(), sha())
        val coloured = result.xml(FOREGROUND_PATH)
        val monochrome = result.xml(MONO_PATH)

        // Every coloured band reappears verbatim, so no hole drifted off its band.
        for (fill in listOf(MAIN_FILL, SHA_FILL)) {
            assertTrue("android:pathData=\"${quadPathOf(coloured, fill)} M " in monochrome, monochrome)
        }
    }

    @Test
    fun `each banner punches at its own monochromeAlpha`() {
        val result = adaptiveIcon(main(), sha(monochromeAlphaPercent = 80.0))

        assertEquals(
            listOf("#FFFFFFFF", "#CCFFFFFF"),
            fillColorsOf(result.xml(MONO_PATH)).takeLast(2),
        )
        // The coloured layer has its own colours and never sees this.
        assertEquals(
            listOf(MAIN_FILL, MAIN_TEXT_FILL, SHA_FILL, SHA_TEXT_FILL),
            fillColorsOf(result.xml(FOREGROUND_PATH)).takeLast(4),
        )
    }

    // --------------------------------------------------------------- warnings

    @Test
    fun `two banners in one corner warn once, naming both`() {
        val result = adaptiveIcon(main(), sha(corner = BannerCorner.TOP_LEFT))

        val warning = result.warnings.single()
        assertTrue("\"main\", \"sha\"" in warning, warning)
        assertTrue("TOP_LEFT" in warning, warning)
        assertTrue("overlap" in warning, warning)
        // Named in paint order, and the loser named again — that is the point of the message.
        assertTrue("\"main\" may end up hidden" in warning, warning)
        assertTrue("iconBanner z" in warning, warning)
    }

    @Test
    fun `banners in different corners do not warn`() {
        assertEquals(emptyList(), adaptiveIcon(main(), sha()).warnings)
    }

    @Test
    fun `two banners of illegibly long text are drawn without complaint`() {
        val result = adaptiveIcon(
            main(text = "STAGING RC1"),
            sha(text = "BUILD 12345678"),
        )

        assertEquals(emptyList(), result.warnings, result.warnings.toString())
        assertTrue(result.files.isNotEmpty(), "neither banner was generated")
    }

    // ---------------------------------------------------------------- helpers

    private fun main(
        text: String = "DEV",
        corner: BannerCorner = BannerCorner.TOP_LEFT,
        monochromeAlphaPercent: Double = 100.0,
    ) = style(
        name = "main", text = text, corner = corner, color = MAIN_FILL, textColor = MAIN_TEXT_FILL,
        monochromeAlphaPercent = monochromeAlphaPercent,
    )

    private fun sha(
        text: String = "abc123",
        corner: BannerCorner = BannerCorner.BOTTOM_RIGHT,
        monochromeAlphaPercent: Double = 100.0,
    ) = style(
        name = "sha", text = text, corner = corner, color = SHA_FILL, textColor = SHA_TEXT_FILL,
        monochromeAlphaPercent = monochromeAlphaPercent,
    )

    private companion object {
        /** The two colours in `foreground.xml`, so a fill-order assertion reads as a whole document. */
        const val GREEN = "#3DDC84"
        const val WHITE = "#FFFFFF"

        const val MAIN_FILL = "#FFE91E63"
        const val MAIN_TEXT_FILL = "#FFFFFFFF"
        const val SHA_FILL = "#FF2196F3"
        const val SHA_TEXT_FILL = "#FF000000"
    }
}
