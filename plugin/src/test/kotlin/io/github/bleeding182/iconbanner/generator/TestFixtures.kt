package io.github.bleeding182.iconbanner.generator

import io.github.bleeding182.iconbanner.api.BannerCorner
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

    // Reversed on purpose: the contract says "in no particular order", so returning insertion order
    // would let an accidental ordering dependency in the generator pass unnoticed.
    override fun find(ref: ResourceRef): List<SourceResource> =
        files[ref]?.reversed().orEmpty()
}

internal fun style(
    text: String = "DEV",
    color: String = "#FFE91E63",
    textColor: String = "#FFFFFFFF",
    corner: BannerCorner = BannerCorner.TOP_LEFT,
    heightPercent: Double = 20.0,
): BannerStyle = BannerStyle(text, color, textColor, corner, heightPercent)

internal fun request(
    resources: FakeResources,
    style: BannerStyle = style(),
    icon: ResourceRef = ResourceRef("mipmap", "ic_launcher"),
    roundIcon: ResourceRef? = null,
): BannerRequest = BannerRequest(style, testFont, icon, roundIcon, resources)

internal fun generate(request: BannerRequest): GenerationResult = DefaultBannerGenerator().generate(request)

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
 * Golden-file comparison. Geometry and glyph outlines are far easier to review as an XML diff than
 * to assert on numerically, and a diff turning up in review is exactly the signal that geometry
 * moved.
 *
 * A missing golden file is written out and the test fails, so adding a case is: write the test, run
 * it once, read the diff, commit. Re-generating an existing one is a deliberate `rm` of the file
 * rather than a flag, because a flag that rewrites every expectation at once is how a real
 * regression gets rubber-stamped.
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
