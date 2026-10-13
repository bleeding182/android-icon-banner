package io.github.bleeding182.iconbanner.generator

import io.github.bleeding182.iconbanner.api.ResourceRef
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The failure side of the policy: a variant that asked for a marking and would get none must fail
 * loudly, because a silently unmarked build is the exact thing the plugin exists to prevent. One
 * file the plugin cannot read is only a warning; [RasterBannerGeneratorTest] draws that line.
 */
class BannerGeneratorFailureTest {

    @Test
    fun `an icon whose only bitmap cannot be decoded fails and names it`() {
        val resources = FakeResources()
            .raster("mipmap-hdpi/ic_launcher.webp", bytes = "not an image".toByteArray())
        val message = generate(request(resources, style())).failureMessage()
        assertMessageContains(
            message,
            "@mipmap/ic_launcher",
            "mipmap-hdpi/ic_launcher.webp",
            "no image reader could decode it",
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
    fun `a framework foreground fails rather than resolving to an app resource of the same name`() {
        val resources = FakeResources()
            .xml(
                "mipmap-anydpi-v26/ic_launcher.xml",
                """
                <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                    <foreground android:drawable="@android:drawable/sym_def_app_icon" />
                </adaptive-icon>
                """.trimIndent(),
            )
            .xml("drawable/sym_def_app_icon.xml", input("foreground.xml"))
        val message = generate(request(resources, style())).failureMessage()
        assertMessageContains(
            message,
            "mipmap-anydpi-v26/ic_launcher.xml",
            "@android:drawable/sym_def_app_icon",
            "framework resource",
        )
    }

    @Test
    fun `a package-qualified foreground fails with the unqualified form to write instead`() {
        val resources = FakeResources()
            .xml("mipmap-anydpi-v26/ic_launcher.xml", adaptiveIconWithForeground("@com.example.app:drawable/ic_launcher_foreground"))
            .xml("drawable/ic_launcher_foreground.xml", input("foreground.xml"))
        val message = generate(request(resources, style())).failureMessage()
        assertMessageContains(
            message,
            "mipmap-anydpi-v26/ic_launcher.xml",
            "@com.example.app:drawable/ic_launcher_foreground",
            "package-qualified references are not supported",
            "write it as @drawable/ic_launcher_foreground",
        )
        assertFalse("not a resource reference" in message, message)
    }

    @Test
    fun `a theme attribute foreground fails and says to point at the drawable directly`() {
        val resources = FakeResources()
            .xml("mipmap-anydpi-v26/ic_launcher.xml", adaptiveIconWithForeground("?attr/launcherForeground"))
        val message = generate(request(resources, style())).failureMessage()
        assertMessageContains(
            message,
            "?attr/launcherForeground",
            "theme attribute",
            "point android:drawable at the drawable or mipmap directly",
        )
        assertFalse("not a resource reference" in message, message)
    }

    @Test
    fun `a padded foreground reference is trimmed as aapt2 does`() {
        val resources = FakeResources()
            .xml("mipmap-anydpi-v26/ic_launcher.xml", adaptiveIconWithForeground(" @drawable/ic_launcher_foreground "))
            .xml("drawable/ic_launcher_foreground.xml", input("foreground.xml"))
        generate(request(resources, style())).success()
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
    fun `a font that cannot draw the text fails before any icon is looked at`() {
        // The build script's own text and font are the easier thing to act on than a missing icon.
        val message = generate(request(FakeResources(), style(text = "DEV 中"))).failureMessage()
        assertMessageContains(message, "中", "no glyph")
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

    /**
     * `android:tint` on the root tints everything the vector draws, the appended banner included: the
     * ribbon and its text come out one colour, and the text is gone. Material icon exports carry one.
     */
    @Test
    fun `a vector with a root tint fails and names the file and attribute`() {
        val resources = FakeResources().xml(
            "drawable/ic_launcher.xml",
            input("foreground.xml").replace("<vector ", "<vector android:tint=\"#FF000000\" "),
        )
        val message = generate(
            request(resources, style(), icon = ResourceRef("drawable", "ic_launcher")),
        ).failureMessage()
        assertMessageContains(message, "drawable/ic_launcher.xml", "android:tint")
    }

    /** A themed icon is tinted by the launcher anyway, and its band is a cutout, not a second colour. */
    @Test
    fun `a monochrome vector with a root tint is still accepted`() {
        val resources = FakeResources()
            .xml("mipmap-anydpi-v26/ic_launcher.xml", input("adaptive_separate_mono.xml"))
            .xml("drawable/ic_launcher_foreground.xml", input("foreground.xml"))
            .xml(
                "drawable/ic_launcher_monochrome.xml",
                input("foreground_24.xml").replace("<vector ", "<vector android:tint=\"#FF000000\" "),
            )
        val result = generate(request(resources, style())).success()
        assertTrue("android:tint" in result.xml("drawable/ic_launcher_monochrome.xml"))
    }

    /**
     * One file, two different banners: a monochrome one for the icon, a coloured one for the round
     * icon. Same-name override gives a resource exactly one version, so this cannot be generated — and
     * it is the user's layout, not a bug, so the message has to say which two roles collided.
     */
    @Test
    fun `a drawable that is one icon's monochrome and another's foreground fails and names both roles`() {
        val resources = FakeResources()
            .xml("mipmap-anydpi-v26/ic_launcher.xml", adaptive(foreground = "fg", monochrome = "shared"))
            .xml("mipmap-anydpi-v26/ic_launcher_round.xml", adaptive(foreground = "shared", monochrome = "shared"))
            .xml("drawable/fg.xml", input("foreground.xml"))
            .xml("drawable/shared.xml", input("foreground.xml"))
        val message = generate(
            request(resources, style(), roundIcon = ResourceRef("mipmap", "ic_launcher_round")),
        ).failureMessage()
        assertMessageContains(
            message,
            "drawable/shared.xml is needed as two different banners",
            "the <monochrome> of mipmap-anydpi-v26/ic_launcher.xml",
            "the <foreground> of mipmap-anydpi-v26/ic_launcher_round.xml",
            "give each role its own drawable",
        )
        assertFalse("Internal error" in message, message)
    }

    /**
     * A bitmap as an adaptive foreground is masked by the system, and as a standalone icon is clipped
     * to its own silhouette — so where it has any transparency, the two banners differ.
     */
    @Test
    fun `a bitmap that is both an adaptive foreground and the round icon fails and names both roles`() {
        val resources = FakeResources()
            .xml(
                "mipmap-anydpi-v26/ic_launcher.xml",
                adaptive(foreground = "ic_fg", monochrome = null, type = "mipmap"),
            )
            .raster("mipmap-hdpi/ic_fg.png", solidPng(48, argb = 0x803DDC84.toInt()))
        val message = generate(
            request(resources, style(), roundIcon = ResourceRef("mipmap", "ic_fg")),
        ).failureMessage()
        assertMessageContains(
            message,
            "mipmap-hdpi/ic_fg.png is needed as two different banners",
            "the <foreground> of mipmap-anydpi-v26/ic_launcher.xml",
            "the launcher icon itself",
        )
        assertFalse("Internal error" in message, message)
    }

    private fun adaptive(foreground: String, monochrome: String?, type: String = "drawable"): String =
        """
        <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
            <foreground android:drawable="@$type/$foreground" />
            ${monochrome?.let { "<monochrome android:drawable=\"@$type/$it\" />" }.orEmpty()}
        </adaptive-icon>
        """.trimIndent()

    @Test
    fun `an unreadable font file fails and names it`() {
        val resources = FakeResources().xml("drawable/ic_launcher.xml", input("foreground.xml"))
        val broken = File.createTempFile("not-a-font", ".ttf").apply {
            writeText("this is not a TrueType file")
            deleteOnExit()
        }
        val request = request(
            resources,
            style(),
            icon = ResourceRef("drawable", "ic_launcher"),
            fontFile = broken,
        )
        assertMessageContains(generate(request).failureMessage(), broken.name, "font")
    }

    private fun adaptiveIconWithForeground(drawable: String): String =
        """
        <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
            <foreground android:drawable="$drawable" />
        </adaptive-icon>
        """.trimIndent()
}
