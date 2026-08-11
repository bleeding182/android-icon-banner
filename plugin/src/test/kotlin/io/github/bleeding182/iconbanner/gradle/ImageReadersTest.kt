package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.generator.webpIcon
import org.junit.jupiter.api.Test
import java.io.File
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The registration mechanism, which the whole raster feature rests on and which no other test would
 * notice breaking: this JVM already has the reader on its classpath, so `ImageIO.read` would decode a
 * webp whether [ImageReaders] worked or not.
 *
 * The jars are therefore loaded a *second* time into a child loader, and every assertion here is about
 * the copy that came from that loader rather than about webp being readable at all.
 */
class ImageReadersTest {

    /**
     * The whole test runtime classpath, which contains the reader jars. The child loader's parent is
     * the platform loader, so it loads its own copies rather than delegating to the ones already here
     * — which is exactly what makes a reader of [ImageReaders]'s making identifiable.
     */
    private val classpath: List<File> =
        System.getProperty("java.class.path").split(File.pathSeparator).map(::File)

    /** The registered webp reader that came from [loader], as an instance ready to decode. */
    private fun webpReaderFrom(loader: ClassLoader) =
        ImageIO.getImageReadersByFormatName("webp").asSequence()
            .firstOrNull { it.javaClass.classLoader === loader }
            ?: fail("No webp reader was registered from the loader ImageReaders built")

    @Test
    fun `a registered reader still decodes after the context class loader is put back`() {
        val before = Thread.currentThread().contextClassLoader

        val loader = ImageReaders.install(classpath)

        // The SPI holds its own class references, so the loader it was found through is not needed
        // afterwards — the feature depends on that, so it is pinned rather than assumed.
        assertSame(before, Thread.currentThread().contextClassLoader, "The context loader was not restored")
        val reader = webpReaderFrom(loader)
        val image = MemoryCacheImageInputStream(webpIcon.inputStream()).use { stream ->
            reader.input = stream
            reader.read(0)
        }
        assertEquals(72, image.width)
        assertEquals(72, image.height)
    }

    @Test
    fun `the same jar set reuses one loader`() {
        // Deliberate caching: the registry keeps the SPI instances it is handed, so a loader per build
        // would leak a loader per build.
        val first = ImageReaders.install(classpath)
        val second = ImageReaders.install(classpath.reversed())

        assertSame(first, second)
    }

    @Test
    fun `a different jar set gets its own loader`() {
        val loader = ImageReaders.install(classpath.take(1))

        assertTrue(loader !== ImageReaders.install(classpath))
    }
}
