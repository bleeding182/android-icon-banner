package io.github.bleeding182.iconbanner.generator

import io.github.bleeding182.iconbanner.api.ResourceRef
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Properties of the rewrite that hold for any input, checked directly rather than through a golden
 * file so a regression points at the cause instead of at a wall of coordinates.
 */
class XmlRewritingTest {

    private val icon = ResourceRef("drawable", "ic_launcher")

    private fun banner(vector: String): String {
        val resources = FakeResources().xml("drawable/ic_launcher.xml", vector)
        return generate(request(resources, style(), icon = icon)).success()
            .files.getValue("drawable/ic_launcher.xml")
    }

    @Test
    fun `an unusual android namespace prefix is preserved`() {
        // Hard-coding the prefix emits attributes bound to no namespace, which aapt2 ignores.
        val output = banner(
            """
            <vector xmlns:a="http://schemas.android.com/apk/res/android"
                a:viewportWidth="108"
                a:viewportHeight="108" />
            """.trimIndent()
        )
        assertTrue("a:pathData=" in output, output)
        assertTrue("android:pathData=" !in output, output)
    }

    @Test
    fun `a vector that does not declare the android namespace gets one`() {
        val output = banner(
            """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:viewportWidth="108"
                android:viewportHeight="108" />
            """.trimIndent()
        )
        assertEquals(1, Regex("xmlns:android").findAll(output).count(), output)
    }

    @Test
    fun `comments and unknown elements inside a vector survive`() {
        val output = banner(
            """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:viewportWidth="108"
                android:viewportHeight="108"
                android:autoMirrored="true">
                <!-- keep me -->
                <some-future-element android:whatever="42" />
            </vector>
            """.trimIndent()
        )
        assertTrue("<!-- keep me -->" in output, output)
        assertTrue("some-future-element" in output, output)
        assertTrue("android:whatever=\"42\"" in output, output)
        assertTrue("android:autoMirrored=\"true\"" in output, output)
    }

    @Test
    fun `input formatting does not leak into the output`() {
        // Byte-identical output is what golden tests and the build cache rest on.
        val header = "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\""
        val compact = banner("$header android:viewportWidth=\"108\" android:viewportHeight=\"108\">" +
            "<path android:pathData=\"M0,0h1v1z\"/></vector>")
        val sprawling = banner(
            "$header\n\n    android:viewportHeight=\"108\"\n    android:viewportWidth=\"108\">\n\n" +
                "        <path\n            android:pathData=\"M0,0h1v1z\"\n        />\n\n</vector>\n"
        )
        val windows = banner(
            "$header\r\n    android:viewportWidth=\"108\"\r\n    android:viewportHeight=\"108\">\r\n" +
                "    <path android:pathData=\"M0,0h1v1z\" />\r\n</vector>\r\n"
        )
        assertEquals(compact, sprawling)
        assertEquals(compact, windows)
    }

    @Test
    fun `special characters in attributes are escaped`() {
        val output = banner(
            """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:viewportWidth="108"
                android:viewportHeight="108"
                android:name="a &amp; b &lt;c&gt; &quot;d&quot;" />
            """.trimIndent()
        )
        assertTrue("android:name=\"a &amp; b &lt;c&gt; &quot;d&quot;\"" in output, output)
    }
}
