package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.api.BannerCorner
import io.github.bleeding182.iconbanner.api.FontSpec
import io.github.bleeding182.iconbanner.generator.solidPng
import io.github.bleeding182.iconbanner.generator.testFont
import io.github.bleeding182.iconbanner.generator.webpIcon
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The reader classpath, driven through the task action rather than a real build.
 *
 * The project here declares **no repositories at all**, so nothing can resolve and nothing reaches the
 * network. What the cases then read is `Configuration.getState()`: whether the task action resolved the
 * classpath at all is the whole question, and a TestKit fixture that reaches mavenCentral resolves it
 * quietly either way.
 */
class ImageReaderClasspathTest {

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
                <application android:icon="@mipmap/ic_launcher" />
            </manifest>
            """.trimIndent(),
        )
        val font = File(dir, "fonts/${fontFileName(defaultFace())}")
        font.parentFile.mkdirs()
        testFont.copyTo(font, overwrite = true)
    }

    private fun write(path: String, content: String) {
        File(dir, path).apply { parentFile.mkdirs() }.writeText(content)
    }

    private fun vectorIcon() = write(
        "src/main/res/mipmap/ic_launcher.xml",
        """
        <vector xmlns:android="http://schemas.android.com/apk/res/android"
            android:viewportWidth="108"
            android:viewportHeight="108" />
        """.trimIndent(),
    )

    /**
     * A webp cut off after its header: a bitmap no reader registered in this JVM can decode.
     *
     * A whole one would not do. The reader is a `testImplementation` dependency, so webp decodes here
     * already, and these cases are about which decode reaches for the classpath rather than about the
     * format — a real webp through a real resolution is
     * [RasterIconFunctionalTest]'s subject.
     */
    private fun unreadableIconResource() {
        val file = File(dir, "src/main/res/mipmap-hdpi/ic_launcher.webp")
        file.parentFile.mkdirs()
        file.writeBytes(webpIcon.readBytes().copyOf(24))
    }

    private fun pngIconResource() {
        val file = File(dir, "src/main/res/mipmap-hdpi/ic_launcher.png")
        file.parentFile.mkdirs()
        file.writeBytes(solidPng(72))
    }

    private fun defaultFace() = FontSpec(BannerDefaults.FONT, BannerDefaults.WEIGHT, BannerDefaults.ITALIC)

    /** The wiring's own configuration, and the wiring's own two properties on the task. */
    private fun generateTask(): IconBannerGenerateTask {
        val readers = imageReaderConfiguration(project)
        val task = project.tasks.register("generateIconBanner", IconBannerGenerateTask::class.java).get()
        task.imageReaderClasspath.setFrom(imageReaderFiles(readers))
        task.imageReaderCoordinates.set(declaredImageReaderCoordinates(project, readers))
        task.variantName.set("devDebug")
        task.banners.set(
            listOf(
                project.objects.newInstance(BannerInput::class.java).apply {
                    name.set(MAIN_BANNER)
                    text.set("DEV")
                    color.set(BannerDefaults.COLOR)
                    textColor.set(BannerDefaults.TEXT_COLOR)
                    monochromeAlpha.set(BannerDefaults.MONOCHROME_ALPHA)
                    corner.set(BannerCorner.TOP_LEFT)
                    position.set(BannerDefaults.POSITION)
                    maxTextSize.set(BannerDefaults.MAX_TEXT_SIZE)
                    lineHeight.set(BannerDefaults.LINE_HEIGHT)
                    z.set(BannerDefaults.Z)
                    fontFamily.set(BannerDefaults.FONT)
                    fontWeight.set(BannerDefaults.WEIGHT)
                    fontItalic.set(BannerDefaults.ITALIC)
                }
            )
        )
        task.fontDirectory.set(project.layout.projectDirectory.dir("fonts"))
        task.manifestFiles.set(listOf(project.layout.projectDirectory.file("src/main/AndroidManifest.xml")))
        task.resourceDirectories.set(listOf(project.layout.projectDirectory.dir("src/main/res")))
        task.resourceDirectoryOrder.set(listOf("src/main/res"))
        task.outputDirectory.set(project.layout.buildDirectory.dir("generated"))
        return task
    }

    @Test
    fun `the configuration resolves the pinned reader and is not consumable`() {
        val configuration = imageReaderConfiguration(project)

        assertTrue(configuration.isCanBeResolved)
        assertFalse(configuration.isCanBeConsumed)
        // Nothing declared, so the pinned version is what a resolution would fetch — and what the
        // task fingerprints. defaultDependencies does not run until the configuration is resolved.
        assertTrue(configuration.dependencies.isEmpty())
        assertEquals(listOf(WEBP_READER_COORDINATES), coordinatesOf(configuration))
    }

    @Test
    fun `a declared version replaces the pinned one, in the task's input too`() {
        val configuration = imageReaderConfiguration(project)
        project.dependencies.add(IMAGE_READER_CONFIGURATION, "com.example:reader:1.2.3")

        assertEquals(listOf("com.example:reader:1.2.3"), coordinatesOf(configuration))
        assertEquals(listOf("com.example:reader:1.2.3"), generateTask().imageReaderCoordinates.get())
    }

    @Test
    fun `a vector icon never resolves the classpath`() {
        vectorIcon()

        generateTask().generate()

        assertTrue(File(dir, "build/generated/mipmap/ic_launcher.xml").isFile)
        // The point of the whole arrangement: no bitmap, no resolution, and so no network.
        assertEquals(Configuration.State.UNRESOLVED, imageReaderConfiguration(project).state)
    }

    @Test
    fun `a png icon never resolves the classpath either`() {
        pngIconResource()

        generateTask().generate()

        assertTrue(File(dir, "build/generated/mipmap-hdpi/ic_launcher.png").isFile)
        // The regression the retry exists to prevent: the JDK reads PNG, so a project with no
        // repository serving the WebP reader must still build. Resolving here would fail this build.
        assertEquals(Configuration.State.UNRESOLVED, imageReaderConfiguration(project).state)
    }

    @Test
    fun `a bitmap the JDK cannot read fails naming the file and the coordinates`() {
        unreadableIconResource()

        val failure = assertThrows<Exception> { generateTask().generate() }

        // Resolved, and leniently, so the empty result is what the message is built from.
        assertEquals(Configuration.State.RESOLVED_WITH_FAILURES, imageReaderConfiguration(project).state)
        val message = failure.message.orEmpty()
        // Both halves: either the readers or the file itself could be the fault, and only the path
        // says which resource to go and look at.
        assertTrue("mipmap-hdpi/ic_launcher.webp" in message, message)
        assertTrue(WEBP_READER_COORDINATES in message, message)
        assertTrue(IMAGE_READER_CONFIGURATION in message, message)
        assertTrue("mavenCentral" in message, message)
        assertTrue("offline" in message, message)
        // Named, so the user knows which variant's build stopped.
        assertTrue("devDebug" in message, message)
    }

    private fun coordinatesOf(configuration: Configuration) =
        declaredImageReaderCoordinates(project, configuration).get()
}
