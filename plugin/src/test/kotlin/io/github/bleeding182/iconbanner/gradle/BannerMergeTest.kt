package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.api.BannerCorner
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Merge precedence has many cases and none of them needs a Gradle build to verify, so they are all
 * here rather than in the TestKit suite.
 *
 * The lists passed to [mergeBanner] are always in AGP's own precedence order — build type, then
 * product flavors in dimension order, then the project-level block.
 */
class BannerMergeTest {

    private fun dsl(configure: IconBannerDsl.() -> Unit = {}): IconBannerDsl =
        project.objects.newInstance(IconBannerDsl::class.java).apply(configure)

    /** The banners a variant made of these blocks gets, in declaration order. */
    private fun merge(vararg blocks: IconBannerDsl): List<ResolvedBanner> = mergeBanner(blocks.toList())

    /**
     * The banner named [name], or null when nothing turned it on.
     *
     * Most cases here are about one banner, and reaching for it by name keeps them reading the way
     * they did when a merge produced at most one.
     */
    private fun mergeOne(vararg blocks: IconBannerDsl, name: String = MAIN_BANNER): ResolvedBanner? =
        merge(*blocks).firstOrNull { it.name == name }

    // ------------------------------------------------------------------- main

    @Test
    fun `no text anywhere means no banner`() {
        assertEquals(emptyList<String>(), merge(dsl(), dsl(), dsl { color.set("#00FF00") }).map { it.name })
    }

    @Test
    fun `project level text applies to every variant`() {
        val merged = mergeOne(dsl(), dsl(), dsl { text = "DEV" })
        assertEquals("DEV", merged?.text?.get())
    }

    @Test
    fun `flavor text beats project text`() {
        val merged = mergeOne(dsl(), dsl { text = "FLAVOR" }, dsl { text = "PROJECT" })
        assertEquals("FLAVOR", merged?.text?.get())
    }

    @Test
    fun `build type text beats flavor text`() {
        val merged = mergeOne(dsl { text = "DEBUG" }, dsl { text = "FLAVOR" }, dsl { text = "PROJECT" })
        assertEquals("DEBUG", merged?.text?.get())
    }

    @Test
    fun `the first flavor dimension wins`() {
        val merged = mergeOne(dsl(), dsl { text = "FIRST" }, dsl { text = "SECOND" }, dsl())
        assertEquals("FIRST", merged?.text?.get())
    }

    @Test
    fun `null on a flavor clears an inherited project text`() {
        assertNull(mergeOne(dsl(), dsl { text = null }, dsl { text = "PROJECT" }))
    }

    @Test
    fun `null on a build type clears a flavor text`() {
        assertNull(mergeOne(dsl { text = null }, dsl { text = "FLAVOR" }, dsl()))
    }

    @Test
    fun `an empty string is a banner with no text`() {
        val merged = mergeOne(dsl(), dsl { text = "" }, dsl())
        assertEquals("", merged?.text?.get())
    }

    @Test
    fun `a provider enables the banner without being evaluated`() {
        val evaluated = AtomicBoolean(false)
        val lazyText = project.providers.provider {
            evaluated.set(true)
            "SHA"
        }

        val merged = mergeOne(dsl(), dsl { text = lazyText }, dsl())

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
        val merged = mergeOne(dsl(), dsl { text = "DEV" }, dsl())!!

        assertEquals(BannerDefaults.COLOR, merged.color.get())
        assertEquals(BannerDefaults.TEXT_COLOR, merged.textColor.get())
        assertEquals(BannerDefaults.MONOCHROME_ALPHA, merged.monochromeAlpha.get())
        assertEquals(BannerDefaults.CORNER, merged.corner.get())
        assertEquals(BannerDefaults.POSITION, merged.position.get())
        assertEquals(BannerDefaults.MAX_TEXT_SIZE, merged.maxTextSize.get())
        assertEquals(BannerDefaults.LINE_HEIGHT, merged.lineHeight.get())
        assertEquals(BannerDefaults.Z, merged.z.get())
        assertEquals(BannerDefaults.FONT, merged.fontFamily.get())
        assertEquals(BannerDefaults.WEIGHT, merged.fontWeight.get())
        assertEquals(BannerDefaults.ITALIC, merged.fontItalic.get())
    }

    @Test
    fun `style is inherited from the project block and overridden per flavor`() {
        val merged = mergeOne(
            dsl(),
            dsl {
                text = "DEV"
                color.set("#0000FF")
            },
            dsl {
                color.set("#00FF00")
                corner.set(BannerCorner.BOTTOM_RIGHT)
                position.set(80)
                maxTextSize.set(18)
                lineHeight.set(1.2)
                font.set("Inter")
                weight.set(400)
                italic.set(true)
            },
        )!!

        assertEquals("#0000FF", merged.color.get())
        assertEquals(BannerCorner.BOTTOM_RIGHT, merged.corner.get())
        assertEquals(80, merged.position.get())
        assertEquals(18, merged.maxTextSize.get())
        assertEquals(1.2, merged.lineHeight.get())
        assertEquals("Inter", merged.fontFamily.get())
        assertEquals(400, merged.fontWeight.get())
        assertEquals(true, merged.fontItalic.get())
    }

    @Test
    fun `build type style beats flavor style`() {
        val merged = mergeOne(
            dsl { color.set("#111111") },
            dsl {
                text = "DEV"
                color.set("#222222")
            },
            dsl { color.set("#333333") },
        )!!

        assertEquals("#111111", merged.color.get())
    }

    @Test
    fun `main is still the whole result when no banner is named`() {
        // Every script written before named banners existed still means what it did.
        val merged = merge(dsl(), dsl { text = "DEV" }, dsl { color.set("#00FF00") })

        assertEquals(listOf(MAIN_BANNER), merged.map { it.name })
        assertEquals("DEV", merged.single().text.get())
        assertEquals("#00FF00", merged.single().color.get())
    }

    // ---------------------------------------------------------- named banners

    @Test
    fun `a named banner is merged alongside main`() {
        val merged = merge(
            dsl(),
            dsl(),
            dsl {
                text = "DEV"
                banner("sha") {
                    text = "abc123"
                    corner.set(BannerCorner.BOTTOM_RIGHT)
                }
            },
        )

        assertEquals(listOf(MAIN_BANNER, "sha"), merged.map { it.name })
        assertEquals(listOf("DEV", "abc123"), merged.map { it.text.get() })
        assertEquals(BannerCorner.TOP_LEFT, merged[0].corner.get())
        assertEquals(BannerCorner.BOTTOM_RIGHT, merged[1].corner.get())
    }

    @Test
    fun `a named banner declared at project level is overridden in a flavor`() {
        val merged = mergeOne(
            dsl(),
            dsl { banner("sha") { color.set("#00FF00") } },
            dsl {
                banner("sha") {
                    text = "abc123"
                    color.set("#FF0000")
                    corner.set(BannerCorner.TOP_RIGHT)
                }
            },
            name = "sha",
        )!!

        // The flavor only mentioned the colour, so everything else still comes from the project block.
        assertEquals("#00FF00", merged.color.get())
        assertEquals("abc123", merged.text.get())
        assertEquals(BannerCorner.TOP_RIGHT, merged.corner.get())
    }

    @Test
    fun `a named banner declared only in a flavor reaches that variant`() {
        val merged = merge(dsl(), dsl { banner("sha") { text = "abc123" } }, dsl { text = "DEV" })

        assertEquals(listOf(MAIN_BANNER, "sha"), merged.map { it.name })
        assertEquals("abc123", merged[1].text.get())
    }

    @Test
    fun `a named banner with no text anywhere is silently no banner`() {
        // A styled-but-textless banner is silently no banner, not an error.
        val merged = merge(dsl(), dsl(), dsl { banner("sha") { color.set("#00FF00") } })

        assertEquals(emptyList<String>(), merged.map { it.name })
    }

    @Test
    fun `remove drops a named banner for the blocks it applies to`() {
        val merged = merge(
            dsl(),
            dsl { banner("sha") { remove() } },
            dsl {
                text = "DEV"
                banner("sha") { text = "abc123" }
            },
        )

        assertEquals(listOf(MAIN_BANNER), merged.map { it.name })
    }

    @Test
    fun `null text in a higher precedence block drops a named banner`() {
        // remove() is sugar for exactly this, so both spellings have to behave the same.
        val merged = merge(
            dsl { banner("sha") { text = null } },
            dsl { banner("sha") { text = "abc123" } },
            dsl { text = "DEV" },
        )

        assertEquals(listOf(MAIN_BANNER), merged.map { it.name })
    }

    @Test
    fun `block level text does not leak into a named banner`() {
        // A banner inheriting the block's text would stamp the same marker twice.
        val merged = merge(dsl(), dsl(), dsl { text = "DEV"; banner("sha") { color.set("#00FF00") } })

        assertEquals(listOf(MAIN_BANNER), merged.map { it.name })
        assertEquals("DEV", merged.single().text.get())
    }

    @Test
    fun `a short marker can sit further out than the banner it shares an icon with`() {
        // What position was added for: the sha beside the environment, pushed out to a tight tab.
        val merged = merge(
            dsl(),
            dsl(),
            dsl {
                text = "STAGING"
                corner.set(BannerCorner.BOTTOM_RIGHT)
                banner("sha") {
                    text = "1a2b3"
                    corner.set(BannerCorner.TOP_LEFT)
                    position.set(85)
                }
            },
        )

        val main = merged.single { it.name == MAIN_BANNER }
        val sha = merged.single { it.name == "sha" }
        assertEquals(BannerDefaults.POSITION, main.position.get())
        assertEquals(85, sha.position.get())
    }

    @Test
    fun `block level properties other than text do reach a named banner`() {
        val merged = merge(
            dsl(),
            dsl(),
            dsl {
                text = "DEV"
                color.set("#00FF00")
                textColor.set("#000000")
                monochromeAlpha.set(70)
                corner.set(BannerCorner.BOTTOM_LEFT)
                position.set(88)
                maxTextSize.set(11)
                lineHeight.set(1.2)
                z.set(4)
                font.set("Inter")
                weight.set(400)
                italic.set(true)
                banner("sha") { text = "abc123" }
            },
        )

        val sha = merged.single { it.name == "sha" }
        assertEquals("#00FF00", sha.color.get())
        assertEquals("#000000", sha.textColor.get())
        assertEquals(70, sha.monochromeAlpha.get())
        assertEquals(BannerCorner.BOTTOM_LEFT, sha.corner.get())
        assertEquals(88, sha.position.get())
        assertEquals(11, sha.maxTextSize.get())
        assertEquals(1.2, sha.lineHeight.get())
        assertEquals(4, sha.z.get())
        assertEquals("Inter", sha.fontFamily.get())
        assertEquals(400, sha.fontWeight.get())
        assertEquals(true, sha.fontItalic.get())
    }

    @Test
    fun `a banner's own property beats the block level default for it`() {
        val merged = mergeOne(
            dsl(),
            dsl(),
            dsl {
                color.set("#00FF00")
                banner("sha") {
                    text = "abc123"
                    color.set("#0000FF")
                }
            },
            name = "sha",
        )!!

        assertEquals("#0000FF", merged.color.get())
    }

    @Test
    fun `a higher precedence block level default beats a lower one for a named banner`() {
        // The banner's own declarations first, then every block's defaults behind them.
        val merged = mergeOne(
            dsl { color.set("#111111") },
            dsl { color.set("#222222") },
            dsl {
                color.set("#333333")
                banner("sha") { text = "abc123" }
            },
            name = "sha",
        )!!

        assertEquals("#111111", merged.color.get())
    }

    @Test
    fun `banner declared with no action still reaches the merge once given text elsewhere`() {
        val merged = merge(
            dsl(),
            dsl { banner("sha").text = "abc123" },
            dsl { banner("sha") },
        )

        assertEquals(listOf("sha"), merged.map { it.name })
    }

    // ------------------------------------------------------------ the reserved name

    @Test
    fun `banner('main') is rejected`() {
        val failure = assertThrows(IllegalArgumentException::class.java) { dsl { banner(MAIN_BANNER) } }

        assertTrue(failure.message!!.contains("reserved"), failure.message)
        assertTrue(failure.message!!.contains("iconBanner { }"), failure.message)
    }

    @Test
    fun `main declared through the banners container is rejected too`() {
        // Groovy gets `banners { main { … } }` free, never going through banner(), so the guard has to
        // sit on the container.
        val created = assertThrows(IllegalArgumentException::class.java) { dsl { banners.create(MAIN_BANNER) } }
        assertTrue(created.message!!.contains("reserved"), created.message)

        val registered = assertThrows(Exception::class.java) { dsl { banners.register(MAIN_BANNER).get() } }
        assertTrue(registered.causes().any { "reserved" in it.message.orEmpty() }, registered.toString())
    }

    private fun Throwable.causes(): List<Throwable> = generateSequence(this) { it.cause }.toList()

    // ------------------------------------------------------------------ order

    @Test
    fun `banners come back in declaration order, not alphabetically`() {
        // The container iterates alphabetically; declaration order is what breaks z ties.
        val merged = merge(
            dsl(),
            dsl(),
            dsl {
                banner("zulu") { text = "Z" }
                banner("alpha") { text = "A" }
            },
        )

        assertEquals(listOf("zulu", "alpha"), merged.map { it.name })
    }

    @Test
    fun `a banner introduced by a lower precedence block comes first`() {
        // The project block's banner paints behind one a flavor added on top.
        val merged = merge(
            dsl { banner("buildType") { text = "B" } },
            dsl { banner("flavor") { text = "F" } },
            dsl { banner("project") { text = "P" } },
        )

        assertEquals(listOf("project", "flavor", "buildType"), merged.map { it.name })
    }

    @Test
    fun `main always comes first, whichever block named the others`() {
        val merged = merge(
            dsl(),
            dsl { banner("sha") { text = "abc123" } },
            dsl { text = "DEV" },
        )

        assertEquals(MAIN_BANNER, merged.first().name)
    }

    @Test
    fun `z is a lazy property, so paint order is decided at execution time`() {
        // The merge never sorts: z is a Provider, read in the generate task.
        val merged = merge(
            dsl(),
            dsl(),
            dsl {
                text = "DEV"
                banner("sha") {
                    text = "abc123"
                    z.set(3)
                }
            },
        )

        assertEquals(listOf(BannerDefaults.Z, 3), merged.map { it.z.get() })
    }

    private companion object {
        /**
         * One project for the whole class: it is only ever used for its `ObjectFactory`, and nothing
         * here mutates project state. A fresh one per test costs more than every merge combined.
         */
        val project: Project = ProjectBuilder.builder().build()
    }
}
