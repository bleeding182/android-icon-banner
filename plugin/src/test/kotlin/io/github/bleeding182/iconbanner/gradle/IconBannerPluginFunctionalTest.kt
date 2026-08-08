package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.api.BannerRequest
import io.github.bleeding182.iconbanner.api.BannerStyle
import io.github.bleeding182.iconbanner.api.GenerationResult
import io.github.bleeding182.iconbanner.api.ResourceRef
import io.github.bleeding182.iconbanner.generator.DefaultBannerGenerator
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Covers only what the pure generator seam cannot: which tasks exist, which icon file they pick up,
 * and how the build behaves on a second run. Each case is a real Gradle build, so there are few.
 */
class IconBannerPluginFunctionalTest {

    private lateinit var fixture: IconBannerFixture

    @BeforeEach
    fun setUp(@TempDir dir: File) {
        fixture = IconBannerFixture(dir).overlay("base")
    }

    private val flavors = """
        |    flavorDimensions += "environment"
        |    productFlavors {
        |        create("dev") { dimension = "environment" }
        |        create("prod") { dimension = "environment" }
        |    }
    """.trimMargin()

    /**
     * Asserts which text actually reached the icon.
     *
     * The banner text ends up as glyph outlines, so it cannot be read back out of the generated
     * XML. Instead the same text is rendered through the same generator with the same defaults and
     * the whole file compared — which also happens to prove that the Gradle layer passes the
     * default style through unaltered. Precedence itself is covered far more cheaply in
     * [BannerMergeTest]; what these builds add is that the merged value survives the trip.
     */
    private fun assertBannerText(expected: String, generated: File) {
        assertEquals(renderForeground(expected), generated.readText())
    }

    private fun renderForeground(text: String): String {
        val result = DefaultBannerGenerator().generate(
            BannerRequest(
                style = BannerStyle(
                    text = text,
                    color = BannerDefaults.COLOR,
                    textColor = BannerDefaults.TEXT_COLOR,
                    corner = BannerDefaults.CORNER,
                    maxTextSizePercent = BannerDefaults.MAX_TEXT_SIZE.toDouble(),
                    lineHeight = BannerDefaults.LINE_HEIGHT,
                ),
                fontFile = fixture.fontFile(),
                icon = ResourceRef("mipmap", "ic_launcher"),
                // The base fixture declares no round icon, so the plugin drops the conventional one.
                roundIcon = null,
                resources = DirectoryResourceLookup(listOf(File(fixture.dir, "src/main/res"))),
            )
        )
        val success = assertInstanceOf(GenerationResult.Success::class.java, result)
        return success.files.getValue("drawable/ic_launcher_foreground.xml")
    }

    @Test
    fun `text on a flavor banners that flavor and leaves the others alone`() {
        fixture.buildScript(
            """
            |$flavors
            |    productFlavors {
            |        named("dev") { iconBanner { text = "DEV" } }
            |    }
            """.trimMargin()
        )

        val tasks = fixture.runner("tasks", "--group=icon banner").build()
        assertTrue(tasks.output.contains("generateDevDebugIconBanner"))
        assertTrue(tasks.output.contains("generateDevReleaseIconBanner"))
        assertFalse(tasks.output.contains("generateProdDebugIconBanner"), tasks.output)

        // --no-build-cache so the task actually executes: another case in this suite populates the
        // shared local cache with an identical configuration, and a cached task logs nothing.
        val build = fixture.runner(":generateDevDebugIconBanner", "--no-build-cache").build()

        assertBannerText("DEV", fixture.generatedForeground("devDebug"))
        // Visible on a plain build, with no --info: shipping a bannered icon to production is the
        // one mistake here that cannot be taken back, and nothing else in the build mentions the
        // override at all.
        assertTrue(
            build.output.contains("icon banner: variant 'devDebug' replaces @mipmap/ic_launcher"),
            build.output,
        )
        assertTrue(build.output.contains("\"DEV\""), build.output)
    }

    @Test
    fun `a flavor scoped block needs no import and does not leak to other flavors`() {
        // Kotlin generates a type-safe accessor for the project-level block but none for container
        // elements. With no candidate on the inner receiver, this same script still compiles and
        // binds to the enclosing android { } receiver, configuring the project-wide defaults — so
        // every variant gets a banner and nothing warns you. The accessors live in
        // org.gradle.kotlin.dsl, which build scripts star-import, precisely to prevent that.
        fixture.buildScript(
            """
            |$flavors
            |    productFlavors {
            |        named("dev") { iconBanner { text = "DEV"; corner = bottomRight } }
            |    }
            """.trimMargin()
        )

        val tasks = fixture.runner("tasks", "--group=icon banner").build()

        assertTrue(tasks.output.contains("generateDevDebugIconBanner"), tasks.output)
        assertFalse(tasks.output.contains("generateProdDebugIconBanner"), tasks.output)
        assertFalse(tasks.output.contains("generateProdReleaseIconBanner"), tasks.output)
    }

    @Test
    fun `build type text overrides flavor text`() {
        fixture.buildScript(
            """
            |$flavors
            |    productFlavors {
            |        named("dev") { iconBanner { text = "DEV" } }
            |    }
            |    buildTypes {
            |        named("debug") { iconBanner { text = "DEBUG" } }
            |    }
            """.trimMargin()
        )

        fixture.runner(":generateDevDebugIconBanner", ":generateDevReleaseIconBanner").build()

        assertBannerText("DEBUG", fixture.generatedForeground("devDebug"))
        assertBannerText("DEV", fixture.generatedForeground("devRelease"))
    }

    @Test
    fun `null on a flavor clears a project level text`() {
        fixture.buildScript(
            """
            |    iconBanner { text = "EVERYWHERE" }
            |$flavors
            |    productFlavors {
            |        named("prod") { iconBanner { text = null } }
            |    }
            """.trimMargin()
        )

        val tasks = fixture.runner("tasks", "--group=icon banner").build()

        assertTrue(tasks.output.contains("generateDevDebugIconBanner"))
        assertFalse(tasks.output.contains("generateProdDebugIconBanner"), tasks.output)
    }

    @Test
    fun `a flavor specific launcher icon is the one that gets bannered`() {
        fixture.overlay("flavor-icon").buildScript(
            """
            |$flavors
            |    productFlavors {
            |        named("dev") { iconBanner { text = "DEV" } }
            |    }
            """.trimMargin()
        )

        fixture.runner(":generateDevDebugIconBanner").build()

        val generated = fixture.generatedForeground("devDebug").readText()
        assertTrue(generated.contains("from_dev_source_set"), generated)
        assertFalse(generated.contains("from_main"), generated)
    }

    @Test
    fun `a provider valued text is not evaluated during configuration`() {
        fixture.buildScript(
            """
            |$flavors
            |    productFlavors {
            |        named("dev") { iconBanner { text = providers.of(RecordingText::class) {
            |            parameters.marker.set(layout.projectDirectory.file("evaluated.txt"))
            |        } } }
            |    }
            """.trimMargin()
        )
        File(fixture.dir, "build.gradle.kts").appendText(
            """

            abstract class RecordingText : ValueSource<String, RecordingText.Params> {
                interface Params : ValueSourceParameters {
                    val marker: RegularFileProperty
                }

                override fun obtain(): String {
                    val marker = parameters.marker.get().asFile
                    marker.parentFile.mkdirs()
                    marker.writeText("evaluated")
                    return "SHA"
                }
            }
            """.trimIndent()
        )
        val marker = File(fixture.dir, "evaluated.txt")

        fixture.runner("help").build()
        assertFalse(marker.exists(), "the provider was read during configuration")

        fixture.runner(":generateDevDebugIconBanner").build()
        assertTrue(marker.exists(), "the provider was never read at all")
        assertBannerText("SHA", fixture.generatedForeground("devDebug"))
    }

    @Test
    fun `a second build is up to date and reuses the configuration cache`() {
        fixture.buildScript(
            """
            |$flavors
            |    productFlavors {
            |        named("dev") { iconBanner { text = "DEV" } }
            |    }
            """.trimMargin()
        )

        // Without this the first build can pull the task straight out of the shared local build
        // cache, which another case in this suite populated with an identical configuration.
        val first = fixture.runner(":generateDevDebugIconBanner", "--no-build-cache").build()
        assertEquals(TaskOutcome.SUCCESS, first.task(":generateDevDebugIconBanner")?.outcome)

        val second = fixture.runner(":generateDevDebugIconBanner", "--no-build-cache").build()
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":generateDevDebugIconBanner")?.outcome)
        // No network call on an incremental build: the font task did not run either.
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":downloadDevDebugIconBannerFont")?.outcome)
        assertTrue(second.output.contains("Configuration cache entry reused"), second.output)
    }

    @Test
    fun `no banner configured anywhere registers no tasks`() {
        fixture.buildScript(flavors)

        val tasks = fixture.runner("tasks", "--all").build()

        assertFalse(tasks.output.contains("IconBanner"), tasks.output)
        assertFalse(fixture.generatedResources("devDebug").exists())
    }
}
