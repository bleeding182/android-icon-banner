package com.github.bleeding182.iconbanner.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

    /** The stub generator records the resolved text in a comment; the real one draws outlines. */
    private fun bannerTextIn(file: File): String? =
        Regex("<!-- iconBanner text: (.*) -->").find(file.readText())?.groupValues?.get(1)

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

        fixture.runner(":generateDevDebugIconBanner").build()

        assertEquals("DEV", bannerTextIn(fixture.generatedForeground("devDebug")))
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

        assertEquals("DEBUG", bannerTextIn(fixture.generatedForeground("devDebug")))
        assertEquals("DEV", bannerTextIn(fixture.generatedForeground("devRelease")))
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
        assertEquals("SHA", bannerTextIn(fixture.generatedForeground("devDebug")))
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
