package io.github.bleeding182.iconbanner.generator

import io.github.bleeding182.iconbanner.api.BannerCorner
import io.github.bleeding182.iconbanner.api.BannerStyle
import io.github.bleeding182.iconbanner.api.GenerationResult
import io.github.bleeding182.iconbanner.api.ResourceRef
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The generator's observable behaviour: the exact resource files a given configuration produces.
 *
 * Run twice — see [BannerGeneratorTest] and [BannerGeneratorGermanLocaleTest] — because the single
 * likeliest way to ship a broken plugin is number formatting that only works under a dot-decimal
 * default locale. Both subclasses assert against the same golden files, so a locale leak anywhere
 * in the generator shows up as a diff rather than as a subtly corrupt icon on someone else's
 * machine.
 */
abstract class BannerGeneratorTestCases {

    /** Default locale in force for every test in this class. */
    protected abstract val locale: Locale

    private lateinit var previousLocale: Locale

    @BeforeEach
    fun applyLocale() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(locale)
    }

    @AfterEach
    fun restoreLocale() {
        Locale.setDefault(previousLocale)
    }

    // ---------------------------------------------------------------- corners

    @Test
    fun `top left corner`() {
        assertMatchesGolden("corner_top_left.xml", plainVector(style(corner = BannerCorner.TOP_LEFT)))
    }

    @Test
    fun `top right corner`() {
        assertMatchesGolden("corner_top_right.xml", plainVector(style(corner = BannerCorner.TOP_RIGHT)))
    }

    @Test
    fun `bottom left corner`() {
        assertMatchesGolden("corner_bottom_left.xml", plainVector(style(corner = BannerCorner.BOTTOM_LEFT)))
    }

    @Test
    fun `bottom right corner`() {
        assertMatchesGolden("corner_bottom_right.xml", plainVector(style(corner = BannerCorner.BOTTOM_RIGHT)))
    }

    // ------------------------------------------------------- coloured vs mono

    @Test
    fun `coloured and monochrome output for the same input`() {
        val result = adaptiveIcon(style())
        // The same golden as the plain vector: an adaptive icon's foreground *is* that vector.
        assertMatchesGolden("corner_top_left.xml", result.xml(FOREGROUND_PATH))
        assertMatchesGolden("shared_monochrome.xml", result.xml(MONO_PATH))
    }

    @Test
    fun `monochromeAlpha becomes the punched fill's alpha, and nothing else`() {
        val result = adaptiveIcon(style(monochromeAlphaPercent = 80.0))
        val monochrome = result.xml(MONO_PATH)

        assertTrue("android:fillColor=\"#CCFFFFFF\"" in monochrome, monochrome)
        // The coloured layer draws its own colours at their own alpha, so it is the default golden.
        assertMatchesGolden("corner_top_left.xml", result.xml(FOREGROUND_PATH))
    }

    @Test
    fun `the alpha percentage spans the full byte`() {
        assertEquals("#00FFFFFF", BannerPainter.monochromeFill(0.0))
        assertEquals("#80FFFFFF", BannerPainter.monochromeFill(50.0))
        assertEquals("#FFFFFFFF", BannerPainter.monochromeFill(100.0))
    }

    @Test
    fun `the monochrome clip is the complement of the band that was drawn`() {
        // The coloured quad and the monochrome clip are derived separately; if they disagree the
        // artwork bleeds into the ribbon.
        val result = adaptiveIcon(style(text = "STAGING"))
        val coloured = result.xml(FOREGROUND_PATH)
        val monochrome = result.xml(MONO_PATH)

        // The punched path is the coloured layer's exact quad, with the glyphs appended as holes.
        assertTrue("android:pathData=\"${quadPathOf(coloured)} M " in monochrome, monochrome)

        // Every clip coordinate is the icon's edge or one of the band's.
        val clip = Regex("<clip-path android:pathData=\"([^\"]+)\"").find(monochrome)
            ?: error("No clip path in $monochrome")
        assertEquals(
            quadPointsOf(coloured).flatMap { listOf(it.first, it.second) }.toSortedSet(),
            pathPoints(clip.groupValues[1]).flatMap { listOf(it.first, it.second) }
                .filter { it != 108.0 }
                .toSortedSet(),
        )
    }

    @Test
    fun `monochrome wrap leaves existing groups and clip paths intact`() {
        val resources = FakeResources()
            .xml(ADAPTIVE_PATH, input("adaptive_shared_mono.xml"))
            .xml(FOREGROUND_PATH, input("foreground_groups.xml"))
        val result = generate(request(resources, style())).success()
        assertMatchesGolden("groups_monochrome.xml", result.xml(MONO_PATH))
        assertMatchesGolden("groups_coloured.xml", result.xml(FOREGROUND_PATH))
    }

    // ------------------------------------------------------------------- text

    @Test
    fun `short text reaches maxTextSize and the band is sized to it`() {
        val output = plainVector(style(text = "QA"))
        assertMatchesGolden("text_short.xml", output)
        // Two characters have length to spare, so the text reaches its full size.
        assertEquals(13.0 / 100.0 * 108.0 * 1.5, bandThicknessOf(output), 0.01)
    }

    @Test
    fun `long text is scaled down and the band narrows with it`() {
        val output = plainVector(style(text = "STAGING RC1"))
        assertMatchesGolden("text_long.xml", output)
        // Eleven characters do not fit, so the text and the band both come out smaller.
        val full = 13.0 / 100.0 * 108.0 * 1.5
        assertTrue(bandThicknessOf(output) < full, "the band should have narrowed from $full")
    }

    @Test
    fun `lineHeight thickens the band without touching the text`() {
        // Checked for both cases that exist: "DEV" reaches maxTextSize, "STAGING RC1" is length-bound.
        // Byte equality, because the outline must not differ at all.
        listOf("DEV", "STAGING RC1").forEach { text ->
            val tight = plainVector(style(text = text, lineHeight = 1.0))
            val loose = plainVector(style(text = text, lineHeight = 2.0))
            assertEquals(glyphPathOf(tight), glyphPathOf(loose), "\"$text\" moved with the line height")
            // Tolerance is two decimals of pathData rounding on each edge, doubled by the ratio.
            assertEquals(2 * bandThicknessOf(tight), bandThicknessOf(loose), 0.02, "\"$text\"")
        }
        val tight = plainVector(style(lineHeight = 1.0))
        val loose = plainVector(style(lineHeight = 2.0))
        assertEquals(13.0 / 100.0 * 108.0, bandThicknessOf(tight), 0.01)
        assertEquals(2 * 13.0 / 100.0 * 108.0, bandThicknessOf(loose), 0.01)
    }

    @Test
    fun `the text always stays inside the band`() {
        // A golden file would not obviously flag text spilling over the ribbon edge.
        BannerCorner.entries.forEach { corner ->
            listOf("QA", "DEV", "STAGING RC1").forEach { text ->
                val output = plainVector(style(text = text, corner = corner))
                // The band the generator drew, not one re-derived: the question is where the text landed.
                val edges = quadPointsOf(output).map { (x, y) -> across(corner, x, y) }
                val band = edges.min()..edges.max()
                pathPoints(glyphPathOf(output)).forEach { (x, y) ->
                    val across = across(corner, x, y)
                    assertTrue(
                        across in band,
                        "\"$text\" in $corner reaches $across across the band at ($x, $y)",
                    )
                }
            }
        }
    }

    @Test
    fun `text too long for the ribbon is drawn smaller rather than refused`() {
        // Eleven characters in the length of three still "fit", at 5.39 of 108 — about 3.6dp on a
        // launcher. Illegibly small on purpose is a choice this plugin leaves to the build script.
        for (text in listOf("QA", "DEV", "STAGING", "STAGING RC1", "BUILD 12345678")) {
            val result = generate(
                request(
                    FakeResources().xml("drawable/ic_launcher.xml", input("foreground.xml")),
                    style(text = text),
                    icon = DRAWABLE_ICON,
                )
            ).success()

            assertTrue(result.files.isNotEmpty(), "\"$text\" produced no banner at all")
            assertEquals(emptyList(), result.warnings, "\"$text\" should not warn about its size")
        }
    }

    @Test
    fun `text is rendered verbatim rather than uppercased`() {
        // Not a golden: "dev" and "DEV" are both a wall of Bézier coordinates, so the file could not
        // show a reviewer which one was drawn. That the two differ at all is the whole property.
        assertNotEquals(
            glyphPathOf(plainVector(style(text = "DEV"))),
            glyphPathOf(plainVector(style(text = "dev"))),
        )
    }

    @Test
    fun `empty text produces a ribbon with no glyph path`() {
        val output = plainVector(style(text = ""))
        assertMatchesGolden("text_empty.xml", output)
        assertEquals(3, Regex("<path").findAll(output).count(), "Expected the two original paths plus the ribbon")
    }

    // ----------------------------------------------------------------- colour

    @Test
    fun `colours with alpha pass through untouched`() {
        assertMatchesGolden(
            "colour_alpha.xml",
            plainVector(style(color = "#80E91E63", textColor = "#B3FFFFFF")),
        )
    }

    // --------------------------------------------------------------- viewport

    @Test
    fun `the banner normalises against a non-default viewport`() {
        val resources = FakeResources().xml("drawable/ic_launcher.xml", input("foreground_24.xml"))
        val result = generate(request(resources, style(), icon = DRAWABLE_ICON)).success()
        assertMatchesGolden("viewport_24.xml", result.xml("drawable/ic_launcher.xml"))
    }

    // -------------------------------------------------------- adaptive icons

    @Test
    fun `a shared foreground and monochrome drawable is redirected to a reserved name`() {
        val result = adaptiveIcon(style())
        assertEquals(
            setOf(ADAPTIVE_PATH, FOREGROUND_PATH, MONO_PATH),
            result.files.keys,
        )
        assertMatchesGolden("adaptive_redirected.xml", result.xml(ADAPTIVE_PATH))
    }

    @Test
    fun `a separate monochrome drawable is bannered in place with no redirect`() {
        val resources = FakeResources()
            .xml("mipmap-anydpi-v26/ic_launcher.xml", input("adaptive_separate_mono.xml"))
            .xml("drawable/ic_launcher_foreground.xml", input("foreground.xml"))
            .xml("drawable/ic_launcher_monochrome.xml", input("foreground_24.xml"))
        val result = generate(request(resources, style())).success()
        // The adaptive icon itself needs no change, so it is not re-emitted and the original stands.
        assertEquals(
            setOf(FOREGROUND_PATH, "drawable/ic_launcher_monochrome.xml"),
            result.files.keys,
        )
        assertMatchesGolden("separate_monochrome.xml", result.xml("drawable/ic_launcher_monochrome.xml"))
    }

    @Test
    fun `an adaptive icon with no monochrome layer is handled without one`() {
        val resources = FakeResources()
            .xml("mipmap-anydpi-v26/ic_launcher.xml", input("adaptive_no_mono.xml"))
            .xml("drawable/ic_launcher_foreground.xml", input("foreground.xml"))
        val files = generate(request(resources, style())).success().files
        assertEquals(setOf(FOREGROUND_PATH), files.keys)
    }

    @Test
    fun `unrecognised elements and attributes survive the rewrite`() {
        // Replacement is whole-file: anything not re-emitted is gone from the app.
        val resources = FakeResources()
            .xml("mipmap-anydpi-v26/ic_launcher.xml", input("adaptive_extras.xml"))
            .xml("drawable/ic_launcher_foreground.xml", input("foreground.xml"))
        val output = generate(request(resources, style())).success().xml(ADAPTIVE_PATH)
        assertMatchesGolden("adaptive_extras_preserved.xml", output)
        assertTrue("future-layer" in output, output)
        assertTrue("tools:ignore" in output, output)
        assertTrue("ic_launcher_background" in output, output)
        assertTrue("Hand-maintained" in output, output)
    }

    @Test
    fun `the background layer is never touched`() {
        val resources = FakeResources()
            .xml("mipmap-anydpi-v26/ic_launcher.xml", input("adaptive_shared_mono.xml"))
            .xml("drawable/ic_launcher_foreground.xml", input("foreground.xml"))
            .xml("drawable/ic_launcher_background.xml", input("foreground.xml"))
        val files = generate(request(resources, style())).success().files
        assertTrue("drawable/ic_launcher_background.xml" !in files, files.keys.toString())
    }

    @Test
    fun `a plain vector icon is bannered directly`() {
        val resources = FakeResources().xml("drawable/ic_launcher.xml", input("foreground.xml"))
        val files = generate(request(resources, style(), icon = DRAWABLE_ICON)).success().files
        assertEquals(setOf("drawable/ic_launcher.xml"), files.keys)
    }

    // --------------------------------------------------------------- coverage

    @Test
    fun `every qualifier variant of a drawable is bannered under its own folder`() {
        val resources = FakeResources()
            .xml("mipmap-anydpi-v26/ic_launcher.xml", input("adaptive_shared_mono.xml"))
            .xml("drawable/ic_launcher_foreground.xml", input("foreground.xml"))
            .xml("drawable-v24/ic_launcher_foreground.xml", input("foreground_groups.xml"))
            // A bitmap variant of the same drawable, which gets the same two passes as the vectors.
            .raster("drawable-hdpi/ic_launcher_foreground.png", solidPng(48))
        val files = generate(request(resources, style())).success().files
        assertEquals(
            setOf(
                ADAPTIVE_PATH,
                "drawable/ic_launcher_foreground.xml",
                "drawable/ic_launcher_foreground_iconbanner_mono.xml",
                "drawable-v24/ic_launcher_foreground.xml",
                "drawable-v24/ic_launcher_foreground_iconbanner_mono.xml",
                "drawable-hdpi/ic_launcher_foreground.png",
                "drawable-hdpi/ic_launcher_foreground_iconbanner_mono.png",
            ),
            files.keys,
        )
    }

    @Test
    fun `icon and round icon sharing a foreground emit each file once`() {
        val resources = FakeResources()
            .xml("mipmap-anydpi-v26/ic_launcher.xml", input("adaptive_shared_mono.xml"))
            .xml("mipmap-anydpi-v26/ic_launcher_round.xml", input("adaptive_shared_mono.xml"))
            .xml("drawable/ic_launcher_foreground.xml", input("foreground.xml"))
        val result = generate(
            request(resources, style(), roundIcon = ResourceRef("mipmap", "ic_launcher_round")),
        ).success()
        assertEquals(
            setOf(
                ADAPTIVE_PATH,
                "mipmap-anydpi-v26/ic_launcher_round.xml",
                FOREGROUND_PATH,
                MONO_PATH,
            ),
            result.files.keys,
        )
        // The shared foreground is reported once, not once per launcher icon that reaches it.
        assertEquals(1, result.info.count { it.startsWith(FOREGROUND_PATH) }, result.info.toString())
    }

    @Test
    fun `displaced resources are reported so the silent override is visible`() {
        // Nothing in the resource merger mentions the override, so this list is the only notice.
        val result = adaptiveIcon(style())
        assertEquals(
            listOf(
                "drawable/ic_launcher_foreground.xml replaced by a bannered copy",
                "drawable/ic_launcher_foreground_iconbanner_mono.xml generated for the monochrome banner",
                "mipmap-anydpi-v26/ic_launcher.xml replaced: <monochrome> redirected to " +
                    "@drawable/ic_launcher_foreground_iconbanner_mono",
            ),
            result.info,
        )
    }

    @Test
    fun `generation is byte-deterministic across runs`() {
        // Attribute order and whitespace are where XML determinism goes; an encoder's timestamp is
        // where a bitmap's does, so the run covers both kinds of output.
        fun run() = generate(
            request(
                FakeResources()
                    .xml(ADAPTIVE_PATH, input("adaptive_shared_mono.xml"))
                    .xml(FOREGROUND_PATH, input("foreground.xml"))
                    .raster("mipmap-hdpi/ic_launcher.webp", solidPng(72)),
                style(),
            )
        ).success()

        assertSameFiles(run(), run())
    }

    // ---------------------------------------------------------------- helpers

    /**
     * How far across the corner diagonal a point sits, measured along the two axes rather than as a
     * true distance. Monotonic in the real distance, so the band is still a plain interval in it and
     * "is this point between the band's edges" is exact; only ratios need the √2 (see
     * [bandThicknessOf]).
     */
    private fun across(corner: BannerCorner, x: Double, y: Double): Double = when (corner) {
        BannerCorner.TOP_LEFT -> x + y
        BannerCorner.TOP_RIGHT -> (108.0 - x) + y
        BannerCorner.BOTTOM_LEFT -> x + (108.0 - y)
        BannerCorner.BOTTOM_RIGHT -> (108.0 - x) + (108.0 - y)
    }

    /**
     * The thickness of the band actually drawn, read back off the quad.
     *
     * [across] measures along the axes, and the band runs at 45°, so its two edges are √2 further
     * apart in that metric than the band is thick. `lineHeight` names the thickness, so the
     * conversion belongs here rather than in the expectations.
     */
    private fun bandThicknessOf(output: String, corner: BannerCorner = BannerCorner.TOP_LEFT): Double {
        val edges = quadPointsOf(output).map { (x, y) -> across(corner, x, y) }
        return (edges.max() - edges.min()) / Math.sqrt(2.0)
    }

}

class BannerGeneratorTest : BannerGeneratorTestCases() {
    override val locale: Locale get() = Locale.ROOT
}

/**
 * The same expectations under the author's own default locale, where `%.2f` formats 23.1 as "23,1".
 */
class BannerGeneratorGermanLocaleTest : BannerGeneratorTestCases() {
    override val locale: Locale get() = Locale.forLanguageTag("de-AT")
}
