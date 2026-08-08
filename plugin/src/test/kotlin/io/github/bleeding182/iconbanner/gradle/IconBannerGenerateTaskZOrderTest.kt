package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.api.BannerCorner
import io.github.bleeding182.iconbanner.generator.testFont
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Paint order, decided inside the generate task.
 *
 * It cannot be settled in the merge: `z` is a lazy [org.gradle.api.provider.Property] like every
 * other, so the earliest anything may read it is execution time. That puts the sort in the task
 * action, and this drives the task action directly rather than through a real build — a TestKit case
 * per ordering would be minutes of Gradle to observe the order of two paths in one file.
 */
class IconBannerGenerateTaskZOrderTest {

    private lateinit var project: Project
    private lateinit var dir: File

    @BeforeEach
    fun setUp(@TempDir temp: File) {
        dir = temp
        project = ProjectBuilder.builder().withProjectDir(temp).build()

        write(
            "src/main/AndroidManifest.xml",
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application android:icon="@drawable/ic_launcher" />
            </manifest>
            """.trimIndent(),
        )
        write(
            "src/main/res/drawable/ic_launcher.xml",
            """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:viewportWidth="108"
                android:viewportHeight="108" />
            """.trimIndent(),
        )
        // The contract between the two tasks: neither knows the other's type.
        val font = File(dir, "fonts/roboto-mono-700.ttf")
        font.parentFile.mkdirs()
        testFont.copyTo(font, overwrite = true)
    }

    private fun write(path: String, content: String) {
        File(dir, path).apply { parentFile.mkdirs() }.writeText(content)
    }

    /** One banner, in a colour that identifies it in the output. */
    private class Banner(val name: String, val color: String, val z: Int?)

    /** Runs the task over [banners] and returns the `fillColor`s of the icon it wrote, in paint order. */
    private fun paintOrder(vararg banners: Banner): List<String> {
        val objects = project.objects
        val task = project.tasks.register("generateIconBanner", IconBannerGenerateTask::class.java).get()
        task.variantName.set("devDebug")
        task.banners.set(
            banners.map { banner ->
                objects.newInstance(BannerInput::class.java).apply {
                    name.set(banner.name)
                    text.set("")
                    color.set(banner.color)
                    textColor.set(BannerDefaults.TEXT_COLOR)
                    monochromeAlpha.set(BannerDefaults.MONOCHROME_ALPHA)
                    corner.set(BannerCorner.TOP_LEFT)
                    position.set(BannerDefaults.POSITION)
                    maxTextSize.set(BannerDefaults.MAX_TEXT_SIZE)
                    lineHeight.set(BannerDefaults.LINE_HEIGHT)
                    z.set(banner.z ?: BannerDefaults.Z)
                    fontFamily.set(BannerDefaults.FONT)
                    fontWeight.set(BannerDefaults.WEIGHT)
                    fontItalic.set(BannerDefaults.ITALIC)
                }
            }
        )
        task.fontDirectory.set(project.layout.projectDirectory.dir("fonts"))
        task.manifestFiles.set(listOf(project.layout.projectDirectory.file("src/main/AndroidManifest.xml")))
        task.resourceDirectories.set(listOf(project.layout.projectDirectory.dir("src/main/res")))
        task.resourceDirectoryOrder.set(listOf("src/main/res"))
        task.outputDirectory.set(project.layout.buildDirectory.dir("generated"))

        task.generate()

        val generated = File(task.outputDirectory.get().asFile, "drawable/ic_launcher.xml").readText()
        return Regex("android:fillColor=\"([^\"]+)\"").findAll(generated).map { it.groupValues[1] }.toList()
    }

    @Test
    fun `banners with no z at all are painted in declaration order`() {
        assertEquals(
            listOf(RED, GREEN, BLUE),
            paintOrder(Banner("a", RED, null), Banner("b", GREEN, null), Banner("c", BLUE, null)),
        )
    }

    @Test
    fun `a higher z is painted last`() {
        assertEquals(
            listOf(GREEN, BLUE, RED),
            paintOrder(Banner("a", RED, 5), Banner("b", GREEN, 0), Banner("c", BLUE, 1)),
        )
    }

    @Test
    fun `a negative z sinks a banner below the ones that never set it`() {
        assertEquals(
            listOf(BLUE, RED, GREEN),
            paintOrder(Banner("a", RED, 0), Banner("b", GREEN, 0), Banner("c", BLUE, -1)),
        )
    }

    @Test
    fun `equal z keeps declaration order`() {
        // Stable sort over a declaration-ordered list, so declaration order is the tie-break.
        assertEquals(
            listOf(GREEN, RED, BLUE),
            paintOrder(Banner("zulu", GREEN, 2), Banner("alpha", RED, 3), Banner("mike", BLUE, 3)),
        )
    }

    @Test
    fun `a bad value is reported against the banner that carries it`() {
        // The first moment a provider-valued colour can be checked at all.
        val failure = assertThrows(IllegalArgumentException::class.java) {
            paintOrder(Banner("a", RED, 0), Banner("sha", "definitely not a colour", 0))
        }

        assertTrue(failure.message!!.contains("banner 'sha'"), failure.message)
        assertTrue(failure.message!!.contains("variant 'devDebug'"), failure.message)
    }

    private companion object {
        const val RED = "#FFFF0000"
        const val GREEN = "#FF00FF00"
        const val BLUE = "#FF0000FF"
    }
}
