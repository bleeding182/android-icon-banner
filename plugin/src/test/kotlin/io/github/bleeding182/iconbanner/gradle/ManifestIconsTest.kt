package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.api.ResourceRef
import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
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

    private fun read(vararg manifests: File) = ManifestIcons.read(manifests.toList(), "devDebug")

    @Test
    fun `reads the declared icon and round icon`() {
        val main = manifest("main.xml", """android:icon="@mipmap/brand" android:roundIcon="@mipmap/brand_round"""")

        val icons = read(main)

        assertEquals(ResourceRef("mipmap", "brand"), icons.icon)
        assertEquals(ResourceRef("mipmap", "brand_round"), icons.roundIcon)
    }

    /**
     * Nothing is assumed for an attribute nobody declared. Android populates `roundIconRes` from
     * `android:roundIcon` alone, so an undeclared `ic_launcher_round` is artwork no launcher loads —
     * bannering it would write output nothing displays, and used to fail the build outright when its
     * bitmaps needed an image reader that could not be resolved.
     */
    @Test
    fun `an undeclared attribute names no icon`() {
        val main = manifest("main.xml", """android:label="@string/app_name"""")

        val icons = read(main)

        assertNull(icons.icon)
        assertNull(icons.roundIcon)
    }

    @Test
    fun `an undeclared round icon is not invented beside a declared icon`() {
        val main = manifest("main.xml", """android:icon="@mipmap/ic_launcher"""")

        val icons = read(main)

        assertEquals(ResourceRef("mipmap", "ic_launcher"), icons.icon)
        assertNull(icons.roundIcon)
    }

    @Test
    fun `the highest priority manifest wins per attribute`() {
        val flavor = manifest("flavor.xml", """android:icon="@mipmap/flavor_icon"""")
        val main = manifest("main.xml", """android:icon="@mipmap/main_icon" android:roundIcon="@mipmap/main_round"""")

        val icons = read(flavor, main)

        assertEquals(ResourceRef("mipmap", "flavor_icon"), icons.icon)
        assertEquals(ResourceRef("mipmap", "main_round"), icons.roundIcon)
    }

    @Test
    fun `a manifest that does not exist is skipped`() {
        val missing = File(dir, "gone.xml")
        val main = manifest("main.xml", """android:icon="@drawable/only"""")

        assertEquals(ResourceRef("drawable", "only"), read(missing, main).icon)
    }

    @Test
    fun `a declaration that is not a resource reference fails rather than being ignored`() {
        val main = manifest("main.xml", """android:icon="ic_launcher"""")

        val failure = assertThrows(GradleException::class.java) { read(main) }

        assertEquals(true, failure.message!!.contains("android:icon=\"ic_launcher\""), failure.message)
        assertEquals(true, failure.message!!.contains("devDebug"), failure.message)
    }

    @Test
    fun `a round icon declaration that is not a resource reference fails too`() {
        val main = manifest("main.xml", """android:icon="@mipmap/ic" android:roundIcon="nonsense"""")

        val failure = assertThrows(GradleException::class.java) { read(main) }

        assertEquals(true, failure.message!!.contains("android:roundIcon=\"nonsense\""), failure.message)
    }
}
