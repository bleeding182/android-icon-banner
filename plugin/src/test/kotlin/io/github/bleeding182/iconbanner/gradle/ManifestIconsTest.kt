package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.api.ResourceRef
import io.github.bleeding182.iconbanner.generator.FakeResources
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ManifestIconsTest {

    @TempDir
    lateinit var dir: File

    private fun manifest(name: String, applicationAttributes: String): File =
        File(dir, name).apply {
            writeText(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <application $applicationAttributes />
                </manifest>
                """.trimIndent()
            )
        }

    @Test
    fun `reads the declared icon and round icon`() {
        val main = manifest("main.xml", """android:icon="@mipmap/brand" android:roundIcon="@mipmap/brand_round"""")

        val icons = ManifestIcons.read(listOf(main))

        assertEquals(ResourceRef("mipmap", "brand"), icons.icon)
        assertEquals(ResourceRef("mipmap", "brand_round"), icons.roundIcon)
        assertTrue(!icons.roundIsFallback)
    }

    @Test
    fun `falls back to the conventional names`() {
        val main = manifest("main.xml", """android:label="@string/app_name"""")

        val icons = ManifestIcons.read(listOf(main))

        assertEquals(ResourceRef("mipmap", "ic_launcher"), icons.icon)
        assertEquals(ResourceRef("mipmap", "ic_launcher_round"), icons.roundIcon)
        assertTrue(icons.roundIsFallback)
    }

    @Test
    fun `the highest priority manifest wins per attribute`() {
        val flavor = manifest("flavor.xml", """android:icon="@mipmap/flavor_icon"""")
        val main = manifest("main.xml", """android:icon="@mipmap/main_icon" android:roundIcon="@mipmap/main_round"""")

        val icons = ManifestIcons.read(listOf(flavor, main))

        assertEquals(ResourceRef("mipmap", "flavor_icon"), icons.icon)
        assertEquals(ResourceRef("mipmap", "main_round"), icons.roundIcon)
    }

    @Test
    fun `a manifest that does not exist is skipped`() {
        val missing = File(dir, "gone.xml")
        val main = manifest("main.xml", """android:icon="@drawable/only"""")

        assertEquals(ResourceRef("drawable", "only"), ManifestIcons.read(listOf(missing, main)).icon)
    }

    // ------------------------------------------------- which round icon gets bannered

    private val invented = DeclaredIcons(
        icon = ResourceRef("mipmap", "ic_launcher"),
        roundIcon = ResourceRef("mipmap", "ic_launcher_round"),
        roundIsFallback = true,
    )

    @Test
    fun `an invented round icon backed only by rasters is dropped`() {
        // The layout this exists for: an app that never migrated to adaptive icons still ships
        // legacy per-density ic_launcher_round.webp files and declares no android:roundIcon. The
        // reference resolves to something, so a mere emptiness check keeps it, and the generator
        // then fails the build with "only raster files were found" over a resource the plugin
        // invented — contradicting the policy that rasters are skipped in silence.
        val resources = FakeResources()
            .raster("mipmap-hdpi/ic_launcher_round.webp")
            .raster("mipmap-xxhdpi/ic_launcher_round.webp")

        assertNull(invented.roundIconToBanner(resources))
    }

    @Test
    fun `an invented round icon with an xml variant is kept`() {
        val resources = FakeResources()
            .raster("mipmap-hdpi/ic_launcher_round.webp")
            .xml("mipmap-anydpi-v26/ic_launcher_round.xml", "<adaptive-icon />")

        assertEquals(ResourceRef("mipmap", "ic_launcher_round"), invented.roundIconToBanner(resources))
    }

    @Test
    fun `an invented round icon that resolves to nothing is dropped`() {
        assertNull(invented.roundIconToBanner(FakeResources()))
    }

    @Test
    fun `a declared round icon is kept even when it resolves badly, so the build still fails`() {
        val declared = invented.copy(roundIsFallback = false)

        assertEquals(
            ResourceRef("mipmap", "ic_launcher_round"),
            declared.roundIconToBanner(FakeResources().raster("mipmap-hdpi/ic_launcher_round.webp")),
        )
        assertEquals(ResourceRef("mipmap", "ic_launcher_round"), declared.roundIconToBanner(FakeResources()))
    }
}
