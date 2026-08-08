package io.github.bleeding182.iconbanner.generator

import io.github.bleeding182.iconbanner.api.BannerCorner
import io.github.bleeding182.iconbanner.api.BannerLayer
import io.github.bleeding182.iconbanner.api.BannerRequest
import io.github.bleeding182.iconbanner.api.BannerStyle
import io.github.bleeding182.iconbanner.api.GenerationResult
import io.github.bleeding182.iconbanner.api.ResourceLookup
import io.github.bleeding182.iconbanner.api.ResourceRef
import io.github.bleeding182.iconbanner.api.SourceResource
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Roboto Mono 700, the same face the plugin's default font configuration resolves to, checked in so
 * the suite never touches the network.
 */
internal val testFont: File by lazy {
    val url = TestResources::class.java.getResource("/font/RobotoMono-Bold.ttf")
        ?: error("Test font missing from src/test/resources/generator/font")
    File(url.toURI())
}

private object TestResources

internal fun readTestResource(path: String): String =
    TestResources::class.java.getResourceAsStream("/generator/$path")
        ?.use { it.readBytes().toString(Charsets.UTF_8) }
        ?: error("Missing test resource generator/$path")

/** A source icon from `generator/input/`. */
internal fun input(name: String): String = readTestResource("input/$name")

internal val DRAWABLE_ICON = ResourceRef("drawable", "ic_launcher")
internal const val ADAPTIVE_PATH = "mipmap-anydpi-v26/ic_launcher.xml"
internal const val FOREGROUND_PATH = "drawable/ic_launcher_foreground.xml"
internal const val MONO_PATH = "drawable/ic_launcher_foreground_iconbanner_mono.xml"

/**
 * A [ResourceLookup] over a plain map, which is all the generator needs: the Gradle layer is
 * responsible for source-set precedence, and by the time a lookup reaches here that is settled.
 */
internal class FakeResources : ResourceLookup {

    private val files = mutableMapOf<ResourceRef, MutableList<SourceResource>>()

    /** Adds an XML file at, for example, `drawable-v24/ic_launcher_foreground.xml`. */
    fun xml(path: String, content: String): FakeResources = add(path, content)

    /** Adds a raster file: present in the lookup, but with no XML for the generator to rewrite. */
    fun raster(path: String): FakeResources = add(path, null)

    private fun add(path: String, content: String?): FakeResources {
        val qualifiers = path.substringBeforeLast('/')
        val fileName = path.substringAfterLast('/')
        val type = qualifiers.substringBefore('-')
        val name = fileName.substringBeforeLast('.')
        files.getOrPut(ResourceRef(type, name)) { mutableListOf() }
            .add(SourceResource(qualifiers, fileName, content))
        return this
    }

    // Reversed: the contract says "no particular order", so an ordering dependency must not pass.
    override fun find(ref: ResourceRef): List<SourceResource> =
        files[ref]?.reversed().orEmpty()
}

/** The documented defaults, so a golden file shows what an ordinary project actually gets. */
internal fun style(
    name: String = "main",
    text: String = "DEV",
    color: String = "#FFE91E63",
    textColor: String = "#FFFFFFFF",
    monochromeAlphaPercent: Double = 100.0,
    corner: BannerCorner = BannerCorner.TOP_LEFT,
    positionPercent: Double = 65.0,
    maxTextSizePercent: Double = 13.0,
    lineHeight: Double = 1.5,
): BannerStyle = BannerStyle(
    name, text, color, textColor, monochromeAlphaPercent, corner, positionPercent,
    maxTextSizePercent, lineHeight,
)

/**
 * A request for one banner per [styles], all drawn in [fontFile], painted in the order given.
 * Variadic, so a multi-banner case is one extra argument. No styles means the default one.
 */
internal fun request(
    resources: FakeResources,
    vararg styles: BannerStyle,
    icon: ResourceRef = ResourceRef("mipmap", "ic_launcher"),
    roundIcon: ResourceRef? = null,
    fontFile: File = testFont,
): BannerRequest = BannerRequest(
    layers = (if (styles.isEmpty()) listOf(style()) else styles.toList())
        .map { BannerLayer(it, fontFile) },
    icon = icon,
    roundIcon = roundIcon,
    resources = resources,
)

internal fun generate(request: BannerRequest): GenerationResult = DefaultBannerGenerator().generate(request)

/** Banners `foreground.xml` as a plain `<vector>` launcher icon and returns the one output. */
internal fun plainVector(vararg styles: BannerStyle): String {
    val resources = FakeResources().xml("drawable/ic_launcher.xml", input("foreground.xml"))
    return generate(request(resources, *styles, icon = DRAWABLE_ICON))
        .success().files.getValue("drawable/ic_launcher.xml")
}

/** The default Android Studio shape: adaptive icon whose foreground and monochrome coincide. */
internal fun adaptiveIcon(vararg styles: BannerStyle): GenerationResult.Success {
    val resources = FakeResources()
        .xml(ADAPTIVE_PATH, input("adaptive_shared_mono.xml"))
        .xml(FOREGROUND_PATH, input("foreground.xml"))
    return generate(request(resources, *styles)).success()
}

/** Every `android:fillColor` in the document, in document order. */
internal fun fillColorsOf(output: String): List<String> =
    Regex("android:fillColor=\"([^\"]+)\"").findAll(output).map { it.groupValues[1] }.toList()

/** Every coordinate pair in a `pathData` string, in order. */
internal fun pathPoints(pathData: String): List<Pair<Double, Double>> =
    Regex("(-?[\\d.]+) (-?[\\d.]+)").findAll(pathData)
        .map { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }
        .toList()

/**
 * The ribbon quad's `pathData`, picked out by its fill.
 *
 * Parsed rather than matched with a regex spanning both attributes: their order in the file is the
 * serialiser's business, and the goldens already pin it.
 */
internal fun quadPathOf(output: String, fill: String = "#FFE91E63"): String =
    AndroidXml.parse(output, "generated").documentElement.childElements()
        .firstOrNull { it.localNameOrTag() == "path" && it.androidAttribute("fillColor") == fill }
        ?.androidAttribute("pathData")
        ?: error("No ribbon quad filled $fill in $output")

internal fun quadPointsOf(output: String, fill: String = "#FFE91E63"): List<Pair<Double, Double>> =
    pathPoints(quadPathOf(output, fill))

/** Every text outline's `pathData`: the paths carrying curve segments. */
internal fun glyphPathsOf(output: String): List<String> =
    Regex("android:pathData=\"(M [^\"]*Q[^\"]*)\"").findAll(output).map { it.groupValues[1] }.toList()

internal fun glyphPathOf(output: String): String =
    glyphPathsOf(output).firstOrNull() ?: error("No glyph path in $output")

internal fun GenerationResult.success(): GenerationResult.Success = when (this) {
    is GenerationResult.Success -> this
    is GenerationResult.Failure -> fail("Expected success but generation failed: $message")
}

internal fun GenerationResult.failureMessage(): String = when (this) {
    is GenerationResult.Failure -> message
    is GenerationResult.Success -> fail("Expected a failure but generation succeeded with ${files.keys}")
}

internal fun assertMessageContains(message: String, vararg fragments: String) {
    fragments.forEach { fragment ->
        assertTrue(fragment in message, "Expected \"$fragment\" in failure message, but was: $message")
    }
}

/**
 * Golden-file comparison: geometry and glyph outlines are easier to review as an XML diff than to
 * assert on numerically.
 *
 * A missing golden is written out and the test fails, so adding a case is write, run, read the diff,
 * commit. Regenerating one is a deliberate `rm`, not a flag — a flag that rewrites every expectation
 * at once is how a real regression gets rubber-stamped.
 */
internal fun assertMatchesGolden(goldenName: String, actual: String) {
    // Working directory of the Gradle test JVM is the plugin project directory.
    val source = File("src/test/resources/generator/golden/$goldenName").absoluteFile
    if (!source.isFile) {
        source.parentFile.mkdirs()
        source.writeText(actual)
        fail("Golden file ${source.path} did not exist. It has been written; review it and re-run.")
    }
    assertEquals(
        source.readText(),
        actual,
        "Golden file generator/golden/$goldenName is out of date. Delete it and re-run to regenerate.",
    )
}
