package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.generator.webpIcon
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A legacy raster mipmap beside the adaptive icon, through a real build. Two cases, because the two
 * formats reach the pixels by different routes: a PNG through the JDK's own reader, a WebP through one
 * the plugin resolves at execution time.
 *
 * Both fixtures are the demo app's hdpi icon, so the pictures are comparable; the PNG is transcoded
 * here through plain `ImageIO` rather than through the generator's own encoder.
 */
class RasterIconFunctionalTest {

    private lateinit var fixture: IconBannerFixture

    @BeforeEach
    fun setUp(@TempDir dir: File) {
        fixture = IconBannerFixture(dir).overlay("base")
    }

    private val devFlavor = """
        |    flavorDimensions += "environment"
        |    productFlavors {
        |        create("dev") { dimension = "environment"; iconBanner { text = "DEV" } }
        |    }
    """.trimMargin()

    /** Opaque red, as [BannerDefaults.COLOR] resolves to; the pixel the band leaves behind. */
    private val ribbonColor = 0xFFFF0000.toInt()

    private fun BufferedImage.pixelsColored(argb: Int): Int =
        (0 until width).sumOf { x -> (0 until height).count { y -> getRGB(x, y) == argb } }

    private fun decode(file: File): BufferedImage =
        assertNotNull(ImageIO.read(file), "${file.name} did not decode")

    /**
     * Asserts the generated bitmap: same folder, same size, band painted.
     *
     * A count of ribbon-coloured pixels rather than one coordinate — the band is clipped to the icon's
     * own silhouette, and where exactly it lands belongs to the painter's tests rather than to a build.
     */
    private fun assertBanneredPng(variant: String, source: BufferedImage) {
        val generated = File(fixture.generatedResources(variant), "mipmap-hdpi/ic_launcher.png")
        assertTrue(generated.isFile, "No bannered bitmap at ${generated.path}")
        val bannered = decode(generated)
        assertEquals(source.width, bannered.width)
        assertEquals(source.height, bannered.height)
        assertEquals(0, source.pixelsColored(ribbonColor), "The source already has the ribbon's colour")
        assertTrue(bannered.pixelsColored(ribbonColor) > 100, "No band on ${generated.path}")
        // The adaptive icon is bannered as ever: the raster pass is an addition, not a replacement.
        assertTrue(fixture.generatedForeground(variant).isFile)
    }

    @Test
    fun `a png launcher icon is bannered under its own qualifier folder and stays up to date`() {
        // PNG deliberately: the JDK reads it, so this case is the common path and does not depend on a
        // reader being registered at all.
        val png = ByteArrayOutputStream().use { bytes ->
            ImageIO.write(decode(webpIcon), "png", bytes)
            bytes.toByteArray()
        }
        fixture.legacyIcon("mipmap-hdpi", "ic_launcher.png", png).buildScript(devFlavor)

        // --no-build-cache: the webp case produces different bytes, but nothing stops a warm cache
        // from carrying this one between runs of the suite.
        val first = fixture.runner(":generateDevDebugIconBanner", "--no-build-cache").build()
        assertEquals(TaskOutcome.SUCCESS, first.task(":generateDevDebugIconBanner")?.outcome)
        assertBanneredPng("devDebug", ImageIO.read(png.inputStream()))

        // The untracked reader classpath must not upset this: a `Configuration` reaching a task is the
        // usual way an up-to-date check or a configuration cache entry stops holding.
        val second = fixture.runner(":generateDevDebugIconBanner", "--no-build-cache").build()
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":generateDevDebugIconBanner")?.outcome)
        assertTrue(second.output.contains("Configuration cache entry reused"), second.output)
    }

    @Test
    fun `a vector only icon builds even when no reader can be resolved`() {
        // The base fixture's icon is all vectors and the reader is pointed at a module that does not
        // exist. Worth a whole build of its own: the configuration cache resolves a task's file
        // collections when it stores its entry, so without the lenient view in imageReaderFiles this
        // build would fail over a reader it never needed — and no unit test can see that moment.
        fixture.buildScript(devFlavor)
        File(fixture.dir, "build.gradle.kts").appendText(
            """

            dependencies {
                "$IMAGE_READER_CONFIGURATION"("io.github.bleeding182:no-such-image-reader:0")
            }
            """.trimIndent()
        )

        val build = fixture.runner(":generateDevDebugIconBanner", "--no-build-cache").build()

        assertEquals(TaskOutcome.SUCCESS, build.task(":generateDevDebugIconBanner")?.outcome)
    }

    @Test
    fun `a webp launcher icon is bannered through the reader resolved at execution time`() {
        // The one case in the suite that needs a repository serving the WebP reader: the JDK has none,
        // so the plugin resolves com.twelvemonkeys.imageio from the fixture's mavenCentral(). The
        // existing cases already resolve AGP itself, so that is how this suite works.
        fixture.legacyIcon("mipmap-hdpi", "ic_launcher.webp", webpIcon.readBytes())
            .buildScript(devFlavor)

        val build = fixture.runner(":generateDevDebugIconBanner", "--no-build-cache").build()

        assertEquals(TaskOutcome.SUCCESS, build.task(":generateDevDebugIconBanner")?.outcome)
        // PNG, because the JDK has no WebP writer. Same name and folder, so it still overrides.
        assertBanneredPng("devDebug", decode(webpIcon))
        assertFalse(
            File(fixture.generatedResources("devDebug"), "mipmap-hdpi/ic_launcher.webp").exists(),
            "The webp was re-emitted, so two files now claim the same resource",
        )
    }
}
