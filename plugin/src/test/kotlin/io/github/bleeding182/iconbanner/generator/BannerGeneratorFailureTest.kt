package io.github.bleeding182.iconbanner.generator

import io.github.bleeding182.iconbanner.api.BannerRequest
import io.github.bleeding182.iconbanner.api.ResourceRef
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The failure side of the policy: a variant that asked for a marking and would get none must fail
 * loudly, because a silently unmarked build is the exact thing the plugin exists to prevent. The
 * asymmetry with silently skipped rasters is deliberate and covered in [BannerGeneratorTestCases].
 */
class BannerGeneratorFailureTest {

    private fun input(name: String) = readTestResource("input/$name")

    @Test
    fun `an icon with no XML at all fails and names the raster files`() {
        val resources = FakeResources()
            .raster("mipmap-hdpi/ic_launcher.webp")
            .raster("mipmap-xxhdpi/ic_launcher.webp")
        val message = generate(request(resources, style())).failureMessage()
        assertMessageContains(
            message,
            "@mipmap/ic_launcher",
            "no XML",
            "mipmap-hdpi/ic_launcher.webp",
            "mipmap-xxhdpi/ic_launcher.webp",
        )
    }

    @Test
    fun `a missing icon resource fails`() {
        val message = generate(request(FakeResources())).failureMessage()
        assertMessageContains(message, "@mipmap/ic_launcher", "not found")
    }

    @Test
    fun `a foreground that is not a vector fails and names the file`() {
        val resources = FakeResources()
            .xml("mipmap-anydpi-v26/ic_launcher.xml", input("adaptive_no_mono.xml"))
            .xml("drawable/ic_launcher_foreground.xml", input("not_a_vector.xml"))
        val message = generate(request(resources, style())).failureMessage()
        assertMessageContains(
            message,
            "mipmap-anydpi-v26/ic_launcher.xml",
            "@drawable/ic_launcher_foreground",
            "drawable/ic_launcher_foreground.xml",
            "<layer-list>",
            "<vector>",
        )
    }

    @Test
    fun `a foreground with no android drawable fails`() {
        val resources = FakeResources()
            .xml("mipmap-anydpi-v26/ic_launcher.xml", input("adaptive_inline_foreground.xml"))
            .xml("drawable/ic_launcher_foreground.xml", input("foreground.xml"))
        val message = generate(request(resources, style())).failureMessage()
        assertMessageContains(
            message,
            "mipmap-anydpi-v26/ic_launcher.xml",
            "<foreground> has no android:drawable",
        )
    }

    @Test
    fun `a missing foreground drawable fails and names the reference`() {
        val resources = FakeResources()
            .xml("mipmap-anydpi-v26/ic_launcher.xml", input("adaptive_no_mono.xml"))
        val message = generate(request(resources, style())).failureMessage()
        assertMessageContains(
            message,
            "mipmap-anydpi-v26/ic_launcher.xml",
            "@drawable/ic_launcher_foreground",
            "not found",
        )
    }

    @Test
    fun `an icon that is neither adaptive nor vector fails`() {
        val resources = FakeResources().xml("mipmap-anydpi-v26/ic_launcher.xml", input("not_a_vector.xml"))
        val message = generate(request(resources, style())).failureMessage()
        assertMessageContains(
            message,
            "@mipmap/ic_launcher",
            "mipmap-anydpi-v26/ic_launcher.xml",
            "<layer-list>",
            "<adaptive-icon>",
        )
    }

    @Test
    fun `unparseable XML fails and names the file`() {
        val resources = FakeResources()
            .xml("mipmap-anydpi-v26/ic_launcher.xml", "<adaptive-icon><foreground></adaptive-icon>")
        val message = generate(request(resources, style())).failureMessage()
        assertMessageContains(message, "mipmap-anydpi-v26/ic_launcher.xml", "not valid XML")
    }

    @Test
    fun `a vector without a viewport fails`() {
        val resources = FakeResources().xml(
            "drawable/ic_launcher.xml",
            """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:width="108dp"
                android:height="108dp" />
            """.trimIndent(),
        )
        val message = generate(
            request(resources, style(), icon = ResourceRef("drawable", "ic_launcher")),
        ).failureMessage()
        assertMessageContains(message, "drawable/ic_launcher.xml", "android:viewportWidth")
    }

    @Test
    fun `a character the font cannot display fails and names it`() {
        val resources = FakeResources()
            .xml("mipmap-anydpi-v26/ic_launcher.xml", input("adaptive_no_mono.xml"))
            .xml("drawable/ic_launcher_foreground.xml", input("foreground.xml"))
        // Roboto Mono covers Latin, Greek and Cyrillic but no CJK.
        val message = generate(request(resources, style(text = "DEV 中"))).failureMessage()
        assertMessageContains(message, "中", "U+4E2D", "no glyph", "DEV")
    }

    @Test
    fun `a taken reserved monochrome name fails rather than clobbering it`() {
        val resources = FakeResources()
            .xml("mipmap-anydpi-v26/ic_launcher.xml", input("adaptive_shared_mono.xml"))
            .xml("drawable/ic_launcher_foreground.xml", input("foreground.xml"))
            .xml("drawable/ic_launcher_foreground_iconbanner_mono.xml", input("foreground_24.xml"))
        val message = generate(request(resources, style())).failureMessage()
        assertMessageContains(
            message,
            "@drawable/ic_launcher_foreground_iconbanner_mono",
            "already exists",
        )
    }

    @Test
    fun `an unreadable font file fails and names it`() {
        val resources = FakeResources().xml("drawable/ic_launcher.xml", input("foreground.xml"))
        val broken = File.createTempFile("not-a-font", ".ttf").apply {
            writeText("this is not a TrueType file")
            deleteOnExit()
        }
        val request = BannerRequest(
            style = style(),
            fontFile = broken,
            icon = ResourceRef("drawable", "ic_launcher"),
            roundIcon = null,
            resources = resources,
        )
        assertMessageContains(generate(request).failureMessage(), broken.name, "font")
    }
}
