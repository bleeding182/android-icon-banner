package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.api.ResourceRef
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DirectoryResourceLookupTest {

    @TempDir
    lateinit var root: File

    private fun res(sourceSet: String, path: String, content: String = "<vector/>"): File =
        File(root, "$sourceSet/res/$path").apply {
            parentFile.mkdirs()
            writeText(content)
        }

    private fun lookup(vararg sourceSets: String) =
        DirectoryResourceLookup(sourceSets.map { File(root, "$it/res") })

    @Test
    fun `returns every qualifier variant of a resource`() {
        res("main", "drawable/ic.xml")
        res("main", "drawable-v24/ic.xml")
        res("main", "drawable-night/ic.xml")
        res("main", "drawable/other.xml")

        val found = lookup("main").find(ResourceRef("drawable", "ic"))

        assertEquals(
            setOf("drawable/ic.xml", "drawable-night/ic.xml", "drawable-v24/ic.xml"),
            found.map { it.relativePath }.toSet(),
        )
    }

    @Test
    fun `the highest priority source set wins for the same qualifier`() {
        res("dev", "drawable/ic.xml", "<vector>flavor</vector>")
        res("main", "drawable/ic.xml", "<vector>main</vector>")

        val found = lookup("dev", "main").find(ResourceRef("drawable", "ic"))

        assertEquals(1, found.size)
        assertEquals("<vector>flavor</vector>", found.single().xml)
    }

    @Test
    fun `a qualifier only the lower priority source set has still shows through`() {
        res("dev", "drawable/ic.xml", "<vector>flavor</vector>")
        res("main", "drawable/ic.xml", "<vector>main</vector>")
        res("main", "drawable-v24/ic.xml", "<vector>main v24</vector>")

        val found = lookup("dev", "main").find(ResourceRef("drawable", "ic")).associateBy { it.relativePath }

        assertEquals("<vector>flavor</vector>", found.getValue("drawable/ic.xml").xml)
        assertEquals("<vector>main v24</vector>", found.getValue("drawable-v24/ic.xml").xml)
    }

    @Test
    fun `a non-xml file is reported as bytes, not as missing content`() {
        val file = res("main", "mipmap-hdpi/ic_launcher.webp", "not xml")

        val found = lookup("main").find(ResourceRef("mipmap", "ic_launcher")).single()

        assertEquals("mipmap-hdpi/ic_launcher.webp", found.relativePath)
        assertNull(found.xml)
        // The pixels are what a bannered bitmap is composited onto, so they have to arrive intact.
        assertArrayEquals(file.readBytes(), found.bytes)
    }

    @Test
    fun `an absent resource returns nothing`() {
        res("main", "drawable/ic.xml")

        assertTrue(lookup("main").find(ResourceRef("drawable", "missing")).isEmpty())
    }

    @Test
    fun `resource type matching ignores qualifiers and file extensions`() {
        res("main", "mipmap-anydpi-v26/ic_launcher.xml", "<adaptive-icon/>")
        res("main", "drawable/ic_launcher.xml", "<vector/>")

        val found = lookup("main").find(ResourceRef("mipmap", "ic_launcher"))

        assertEquals(listOf("mipmap-anydpi-v26/ic_launcher.xml"), found.map { it.relativePath })
    }
}
