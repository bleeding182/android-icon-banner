package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.api.BannerCorner
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Merge precedence has many cases and none of them needs a Gradle build to verify, so they are all
 * here rather than in the TestKit suite.
 */
class BannerMergeTest {

    private lateinit var project: Project

    @BeforeEach
    fun setUp(@TempDir dir: File) {
        project = ProjectBuilder.builder().withProjectDir(dir).build()
    }

    private fun dsl(configure: IconBannerDsl.() -> Unit = {}): IconBannerDsl =
        project.objects.newInstance(IconBannerDsl::class.java).apply(configure)

    @Test
    fun `no text anywhere means no banner`() {
        assertNull(mergeBanner(listOf(dsl(), dsl(), dsl { color.set("#00FF00") })))
    }

    @Test
    fun `project level text applies to every variant`() {
        val merged = mergeBanner(listOf(dsl(), dsl(), dsl { text = "DEV" }))
        assertEquals("DEV", merged?.text?.get())
    }

    @Test
    fun `flavor text beats project text`() {
        val merged = mergeBanner(listOf(dsl(), dsl { text = "FLAVOR" }, dsl { text = "PROJECT" }))
        assertEquals("FLAVOR", merged?.text?.get())
    }

    @Test
    fun `build type text beats flavor text`() {
        val merged = mergeBanner(listOf(dsl { text = "DEBUG" }, dsl { text = "FLAVOR" }, dsl { text = "PROJECT" }))
        assertEquals("DEBUG", merged?.text?.get())
    }

    @Test
    fun `the first flavor dimension wins`() {
        val merged = mergeBanner(listOf(dsl(), dsl { text = "FIRST" }, dsl { text = "SECOND" }, dsl()))
        assertEquals("FIRST", merged?.text?.get())
    }

    @Test
    fun `null on a flavor clears an inherited project text`() {
        assertNull(mergeBanner(listOf(dsl(), dsl { text = null }, dsl { text = "PROJECT" })))
    }

    @Test
    fun `null on a build type clears a flavor text`() {
        assertNull(mergeBanner(listOf(dsl { text = null }, dsl { text = "FLAVOR" }, dsl())))
    }

    @Test
    fun `an empty string is a banner with no text`() {
        val merged = mergeBanner(listOf(dsl(), dsl { text = "" }, dsl()))
        assertEquals("", merged?.text?.get())
    }

    @Test
    fun `a provider enables the banner without being evaluated`() {
        val evaluated = AtomicBoolean(false)
        val lazyText = project.providers.provider {
            evaluated.set(true)
            "SHA"
        }

        val merged = mergeBanner(listOf(dsl(), dsl { text = lazyText }, dsl()))

        assertNotNull(merged)
        assertFalse(evaluated.get(), "assigning a provider must not read it")
        assertEquals("SHA", merged?.text?.get())
    }

    @Test
    fun `the getter hands a provider back unevaluated`() {
        val evaluated = AtomicBoolean(false)
        val lazyText = project.providers.provider {
            evaluated.set(true)
            "SHA"
        }
        val block = dsl { text = lazyText }

        assertEquals(lazyText, block.text)
        assertFalse(evaluated.get())
    }

    @Test
    fun `unset style falls back to the documented defaults`() {
        val merged = mergeBanner(listOf(dsl(), dsl { text = "DEV" }, dsl()))!!

        assertEquals(BannerDefaults.COLOR, merged.color.get())
        assertEquals(BannerDefaults.TEXT_COLOR, merged.textColor.get())
        assertEquals(BannerDefaults.CORNER, merged.corner.get())
        assertEquals(BannerDefaults.MAX_TEXT_SIZE, merged.maxTextSize.get())
        assertEquals(BannerDefaults.LINE_HEIGHT, merged.lineHeight.get())
        assertEquals(BannerDefaults.FONT, merged.fontFamily.get())
        assertEquals(BannerDefaults.WEIGHT, merged.fontWeight.get())
        assertEquals(BannerDefaults.ITALIC, merged.fontItalic.get())
    }

    @Test
    fun `style is inherited from the project block and overridden per flavor`() {
        val merged = mergeBanner(
            listOf(
                dsl(),
                dsl {
                    text = "DEV"
                    color.set("#0000FF")
                },
                dsl {
                    color.set("#00FF00")
                    corner.set(BannerCorner.BOTTOM_RIGHT)
                    maxTextSize.set(18)
                    lineHeight.set(1.2)
                    font.set("Inter")
                    weight.set(400)
                    italic.set(true)
                },
            )
        )!!

        assertEquals("#0000FF", merged.color.get())
        assertEquals(BannerCorner.BOTTOM_RIGHT, merged.corner.get())
        assertEquals(18, merged.maxTextSize.get())
        assertEquals(1.2, merged.lineHeight.get())
        assertEquals("Inter", merged.fontFamily.get())
        assertEquals(400, merged.fontWeight.get())
        assertEquals(true, merged.fontItalic.get())
    }

    @Test
    fun `build type style beats flavor style`() {
        val merged = mergeBanner(
            listOf(
                dsl { color.set("#111111") },
                dsl {
                    text = "DEV"
                    color.set("#222222")
                },
                dsl { color.set("#333333") },
            )
        )!!

        assertEquals("#111111", merged.color.get())
    }
}
