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
        val files = adaptiveIcon(style()).files
        assertMatchesGolden("shared_coloured_foreground.xml", files.getValue(FOREGROUND_PATH))
        assertMatchesGolden("shared_monochrome.xml", files.getValue(MONO_PATH))
    }

    @Test
    fun `monochrome punches the text out of the ribbon`() {
        val monochrome = adaptiveIcon(style()).files.getValue(MONO_PATH)
        // One path element carrying both the ribbon and the glyphs, so even-odd can make holes.
        assertTrue("android:fillType=\"evenOdd\"" in monochrome, monochrome)
        assertEquals(1, Regex("android:fillType").findAll(monochrome).count())
        assertTrue("<clip-path" in monochrome, monochrome)
        // The tinted layer only keeps alpha, so the fill is a fixed opaque white.
        assertTrue("android:fillColor=\"#FFFFFFFF\"" in monochrome, monochrome)
    }

    @Test
    fun `the monochrome clip is the complement of the band that was drawn`() {
        // Both outputs size their band from the same text, but they derive their paths separately —
        // the quad for the coloured layer, the inverse clip and the punched path for the monochrome
        // one. If those ever disagree the punched region no longer matches the band, and the icon's
        // own artwork bleeds into the ribbon or a strip of the ribbon goes missing.
        val files = adaptiveIcon(style(text = "STAGING")).files
        val coloured = files.getValue(FOREGROUND_PATH)
        val monochrome = files.getValue(MONO_PATH)

        // The punched path is the coloured layer's exact quad, with the glyphs appended as holes.
        assertTrue("android:pathData=\"${quadPathOf(coloured)} M " in monochrome, monochrome)

        // And the clip is its complement: every coordinate in it is either the icon's own edge or
        // one of the band's two edges, so the clip cannot cut into or fall short of the band.
        val clip = Regex("<clip-path android:pathData=\"([^\"]+)\"").find(monochrome)
            ?: error("No clip path in $monochrome")
        assertEquals(
            quadPointsOf(coloured).flatMap { listOf(it.first, it.second) }.toSortedSet(),
            textPoints(clip.groupValues[1]).flatMap { listOf(it.first, it.second) }
                .filter { it != 108.0 }
                .toSortedSet(),
        )
    }

    @Test
    fun `monochrome wrap leaves existing groups and clip paths intact`() {
        val resources = FakeResources()
            .xml("mipmap-anydpi-v26/ic_launcher.xml", input("adaptive_shared_mono.xml"))
            .xml("drawable/ic_launcher_foreground.xml", input("foreground_groups.xml"))
        val files = generate(request(resources, style())).success().files
        assertMatchesGolden("groups_monochrome.xml", files.getValue(MONO_PATH))
        assertMatchesGolden("groups_coloured.xml", files.getValue(FOREGROUND_PATH))
    }

    // ------------------------------------------------------------------- text

    @Test
    fun `short text reaches maxTextSize and the band is sized to it`() {
        val output = plainVector(style(text = "QA"))
        assertMatchesGolden("text_short.xml", output)
        // Two characters have length to spare, so the text is exactly as large as it was allowed to
        // be and the band is exactly that times the line height.
        assertEquals(13.0 / 100.0 * 108.0 * 1.5, bandWidthOf(output), 0.01)
    }

    @Test
    fun `long text is scaled down and the band narrows with it`() {
        val output = plainVector(style(text = "STAGING RC1"))
        assertMatchesGolden("text_long.xml", output)
        // The point of deriving the band from the text: eleven characters do not fit at the size
        // asked for, so both the text and the band around it come out smaller.
        val full = 13.0 / 100.0 * 108.0 * 1.5
        assertTrue(bandWidthOf(output) < full, "the band should have narrowed from $full")
    }

    @Test
    fun `lineHeight thickens the band without touching the text`() {
        // The two knobs are meant to be independent: maxTextSize sizes the text, lineHeight only
        // decides how much band is wrapped around it. "DEV" reaches maxTextSize either way, so the
        // glyph path has to come out byte-identical.
        val tight = plainVector(style(lineHeight = 1.0))
        val loose = plainVector(style(lineHeight = 2.0))
        assertEquals(glyphPathOf(tight), glyphPathOf(loose))
        assertEquals(13.0 / 100.0 * 108.0, bandWidthOf(tight), 0.01)
        assertEquals(2 * 13.0 / 100.0 * 108.0, bandWidthOf(loose), 0.01)
    }

    @Test
    fun `the text always stays inside the band`() {
        // The golden files pin the exact outline but would not obviously flag text that has spilled
        // over the ribbon edge. This checks the property the sizing exists to guarantee, for both
        // the size-bound and the length-bound case, in every corner.
        // Derived, not hardcoded: retuning the ribbon's position should not need this edited.
        BannerCorner.entries.forEach { corner ->
            listOf("QA", "DEV", "STAGING RC1").forEach { text ->
                val output = plainVector(style(text = text, corner = corner))
                // The band the generator actually drew, not one re-derived from the geometry: the
                // question is whether the text landed inside that quad.
                val edges = quadPointsOf(output).map { (x, y) -> across(corner, x, y) }
                val band = edges.min()..edges.max()
                textPoints(glyphPathOf(output)).forEach { (x, y) ->
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
    fun `text squeezed past readability warns without failing`() {
        // The fit has no floor: eleven characters in the length of three still "fit", at a cap
        // height of about 5.4 of 108 — roughly 3.6dp on a launcher icon, which is a smear. Only the
        // user can decide whether that text is worth the size, so this warns rather than fails.
        val result = generate(
            request(
                FakeResources().xml("drawable/ic_launcher.xml", input("foreground.xml")),
                style(text = "STAGING RC1"),
                icon = DRAWABLE_ICON,
            )
        ).success()

        val warning = result.warnings.single()
        assertTrue("STAGING RC1" in warning, warning)
        assertTrue("11 characters" in warning, warning)
        // The size it landed at, and what that means on a device, both in the message.
        assertTrue("5.4" in warning, warning)
        assertTrue("dp" in warning, warning)
        // No knob is suggested: the length it ran out of is fixed geometry, so there is none.
        assertTrue("shorter text" in warning, warning)
        assertTrue(result.files.isNotEmpty(), "the banner should still have been generated")
    }

    @Test
    fun `text that fits legibly does not warn`() {
        for (text in listOf("QA", "DEV", "DEBUG", "STAGING")) {
            val result = generate(
                request(
                    FakeResources().xml("drawable/ic_launcher.xml", input("foreground.xml")),
                    style(text = text),
                    icon = DRAWABLE_ICON,
                )
            ).success()
            assertEquals(emptyList(), result.warnings, "\"$text\" should be legible at the default style")
        }
    }

    @Test
    fun `one illegible text warns once, not once per icon file`() {
        // An adaptive icon fits the same text three times over: foreground, monochrome, and each
        // qualifier variant. Three identical warnings would read as three separate problems.
        val resources = FakeResources()
            .xml("mipmap-anydpi-v26/ic_launcher.xml", input("adaptive_shared_mono.xml"))
            .xml("drawable/ic_launcher_foreground.xml", input("foreground.xml"))
            .xml("drawable-v24/ic_launcher_foreground.xml", input("foreground_groups.xml"))
        val result = generate(request(resources, style(text = "STAGING RC1"))).success()

        assertEquals(1, result.warnings.size, result.warnings.toString())
    }

    @Test
    fun `text is rendered verbatim rather than uppercased`() {
        assertMatchesGolden("text_mixed_case.xml", plainVector(style(text = "dev")))
    }

    @Test
    fun `empty text produces a ribbon with no glyph path`() {
        val output = plainVector(style(text = ""))
        assertMatchesGolden("text_empty.xml", output)
        assertEquals(3, Regex("<path").findAll(output).count(), "Expected the two original paths plus the ribbon")
    }

    // ----------------------------------------------------------------- colour

    @Test
    fun `colours with alpha and resource references pass through untouched`() {
        val output = plainVector(style(color = "#80E91E63", textColor = "@color/banner_text"))
        assertMatchesGolden("colour_alpha_and_reference.xml", output)
        assertTrue("android:fillColor=\"#80E91E63\"" in output, output)
        assertTrue("android:fillColor=\"@color/banner_text\"" in output, output)
    }

    // --------------------------------------------------------------- viewport

    @Test
    fun `the banner normalises against a non-default viewport`() {
        val resources = FakeResources().xml("drawable/ic_launcher.xml", input("foreground_24.xml"))
        val files = generate(request(resources, style(), icon = DRAWABLE_ICON)).success().files
        assertMatchesGolden("viewport_24.xml", files.getValue("drawable/ic_launcher.xml"))
    }

    // -------------------------------------------------------- adaptive icons

    @Test
    fun `a shared foreground and monochrome drawable is redirected to a reserved name`() {
        val result = adaptiveIcon(style())
        assertEquals(
            setOf(ADAPTIVE_PATH, FOREGROUND_PATH, MONO_PATH),
            result.files.keys,
        )
        assertMatchesGolden("adaptive_redirected.xml", result.files.getValue(ADAPTIVE_PATH))
    }

    @Test
    fun `a separate monochrome drawable is bannered in place with no redirect`() {
        val resources = FakeResources()
            .xml("mipmap-anydpi-v26/ic_launcher.xml", input("adaptive_separate_mono.xml"))
            .xml("drawable/ic_launcher_foreground.xml", input("foreground.xml"))
            .xml("drawable/ic_launcher_monochrome.xml", input("foreground_24.xml"))
        val files = generate(request(resources, style())).success().files
        // The adaptive icon itself needs no change, so it is not re-emitted and the original stands.
        assertEquals(
            setOf(FOREGROUND_PATH, "drawable/ic_launcher_monochrome.xml"),
            files.keys,
        )
        assertMatchesGolden("separate_monochrome.xml", files.getValue("drawable/ic_launcher_monochrome.xml"))
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
        // Replacement is whole-file: anything not re-emitted is gone from the app. A spike lost
        // <monochrome> exactly this way.
        val resources = FakeResources()
            .xml("mipmap-anydpi-v26/ic_launcher.xml", input("adaptive_extras.xml"))
            .xml("drawable/ic_launcher_foreground.xml", input("foreground.xml"))
        val output = generate(request(resources, style())).success().files.getValue(ADAPTIVE_PATH)
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
            .raster("drawable-hdpi/ic_launcher_foreground.png")
        val files = generate(request(resources, style())).success().files
        assertEquals(
            setOf(
                ADAPTIVE_PATH,
                "drawable/ic_launcher_foreground.xml",
                "drawable/ic_launcher_foreground_iconbanner_mono.xml",
                "drawable-v24/ic_launcher_foreground.xml",
                "drawable-v24/ic_launcher_foreground_iconbanner_mono.xml",
            ),
            files.keys,
        )
    }

    @Test
    fun `raster variants are skipped without comment`() {
        val resources = FakeResources()
            .xml("mipmap-anydpi-v26/ic_launcher.xml", input("adaptive_shared_mono.xml"))
            .raster("mipmap-hdpi/ic_launcher.webp")
            .raster("mipmap-xxhdpi/ic_launcher.webp")
            .xml("drawable/ic_launcher_foreground.xml", input("foreground.xml"))
        val result = generate(request(resources, style())).success()
        assertTrue(result.files.keys.none { it.endsWith(".webp") }, result.files.keys.toString())
        assertTrue(result.info.none { "webp" in it }, result.info.toString())
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
        // Nothing in the Gradle resource merger mentions the override, so this list is the only way
        // a user finds out their hand-edited icon was replaced.
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
        // Golden tests and Gradle's build cache both depend on this; DOM attribute order and
        // whitespace handling are the usual places it quietly stops being true.
        val first = adaptiveIcon(style()).files
        val second = adaptiveIcon(style()).files
        assertEquals(first, second)
    }

    // ---------------------------------------------------------------- helpers

    private fun input(name: String) = readTestResource("input/$name")

    /** Every coordinate pair in a `pathData` string, in order. */
    private fun textPoints(pathData: String): List<Pair<Double, Double>> =
        Regex("(-?[\\d.]+) (-?[\\d.]+)").findAll(pathData)
            .map { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }
            .toList()

    /** The ribbon quad's `pathData`, picked out by the fill [style] asked for. */
    private fun quadPathOf(output: String): String =
        Regex("android:fillColor=\"#FFE91E63\"\\s+android:pathData=\"([^\"]+)\"").find(output)
            ?.groupValues?.get(1)
            ?: error("No ribbon quad in $output")

    private fun quadPointsOf(output: String): List<Pair<Double, Double>> = textPoints(quadPathOf(output))

    /** The text outline's `pathData`: the one path carrying curve segments. */
    private fun glyphPathOf(output: String): String =
        Regex("android:pathData=\"(M [^\"]*Q[^\"]*)\"").find(output)?.groupValues?.get(1)
            ?: error("No glyph path in $output")

    /**
     * How far across the corner diagonal a point sits — the axis the band's width is measured along,
     * so the band is a plain interval in it.
     */
    private fun across(corner: BannerCorner, x: Double, y: Double): Double = when (corner) {
        BannerCorner.TOP_LEFT -> x + y
        BannerCorner.TOP_RIGHT -> (108.0 - x) + y
        BannerCorner.BOTTOM_LEFT -> x + (108.0 - y)
        BannerCorner.BOTTOM_RIGHT -> (108.0 - x) + (108.0 - y)
    }

    /** The width of the band actually drawn, read back off the quad. */
    private fun bandWidthOf(output: String, corner: BannerCorner = BannerCorner.TOP_LEFT): Double {
        val edges = quadPointsOf(output).map { (x, y) -> across(corner, x, y) }
        return edges.max() - edges.min()
    }

    /** Banners `foreground.xml` as a plain `<vector>` launcher icon and returns the one output. */
    private fun plainVector(style: BannerStyle): String {
        val resources = FakeResources().xml("drawable/ic_launcher.xml", input("foreground.xml"))
        return generate(request(resources, style, icon = DRAWABLE_ICON))
            .success().files.getValue("drawable/ic_launcher.xml")
    }

    /** The default Android Studio shape: adaptive icon whose foreground and monochrome coincide. */
    private fun adaptiveIcon(style: BannerStyle): GenerationResult.Success {
        val resources = FakeResources()
            .xml("mipmap-anydpi-v26/ic_launcher.xml", input("adaptive_shared_mono.xml"))
            .xml("drawable/ic_launcher_foreground.xml", input("foreground.xml"))
        return generate(request(resources, style)).success()
    }

    private companion object {
        val DRAWABLE_ICON = ResourceRef("drawable", "ic_launcher")
        const val ADAPTIVE_PATH = "mipmap-anydpi-v26/ic_launcher.xml"
        const val FOREGROUND_PATH = "drawable/ic_launcher_foreground.xml"
        const val MONO_PATH = "drawable/ic_launcher_foreground_iconbanner_mono.xml"
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
