package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.api.FontSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The font task and the generate task agree on where a face lives purely by both calling this, so
 * anything that makes it disagree with itself — or produce a name a filesystem will not take — leaves
 * a variant looking for a file nobody wrote.
 */
class FontFileNameTest {

    private fun name(family: String, weight: Int = 700, italic: Boolean = false) =
        fontFileName(FontSpec(family, weight, italic))

    @Test
    fun `the face is spelled out in the name`() {
        assertEquals("roboto-mono-700.ttf", name("Roboto Mono"))
        assertEquals("roboto-mono-400-italic.ttf", name("Roboto Mono", weight = 400, italic = true))
        assertEquals("inter-100.ttf", name("Inter", weight = 100))
    }

    @Test
    fun `anything outside lowercase ASCII alphanumerics collapses to a single hyphen`() {
        assertEquals("pt-sans-narrow-700.ttf", name("PT Sans Narrow"))
        assertEquals("m-plus-1p-700.ttf", name("M PLUS 1p"))
        assertEquals("libre-baskerville-700.ttf", name("Libre  ---  Baskerville"))
        assertEquals("noto-sans-jp-700.ttf", name("Noto Sans JP"))
    }

    @Test
    fun `a trailing run of separators is trimmed rather than left dangling`() {
        assertEquals("inter-700.ttf", name("Inter "))
        assertEquals("inter-700.ttf", name("Inter!!!"))
        // And a leading run never opens the name with a hyphen.
        assertEquals("inter-700.ttf", name("  Inter"))
    }

    @Test
    fun `a family with no ASCII in it at all still yields a name`() {
        // Total by construction: an unusable family is the font layer's problem to report.
        assertEquals("font-700.ttf", name("日本語"))
        assertEquals("font-700.ttf", name(""))
        assertEquals("font-400-italic.ttf", name("---", weight = 400, italic = true))
    }
}
