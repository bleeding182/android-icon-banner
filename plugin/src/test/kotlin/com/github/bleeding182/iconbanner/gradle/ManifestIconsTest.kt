package com.github.bleeding182.iconbanner.gradle

import com.github.bleeding182.iconbanner.api.ResourceRef
import org.junit.jupiter.api.Assertions.assertEquals
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
}
