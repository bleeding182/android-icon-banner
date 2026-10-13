package io.github.bleeding182.iconbanner.gradle

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

class FontCacheDirectoryTest {

    private val root = File("/work/app").absoluteFile
    private val gradleUserHome = File("/home/dev/.gradle").absoluteFile

    @Test
    fun `defaults to the gradle user home`() {
        assertEquals(
            gradleUserHome.resolve("caches/android-icon-banner/fonts"),
            fontCacheDirectory(null, root, gradleUserHome),
        )
    }

    @Test
    fun `a relative override resolves against the build root, so every module shares one cache`() {
        assertEquals(
            root.resolve("ci/fonts"),
            fontCacheDirectory("ci/fonts", root, gradleUserHome),
        )
    }

    @Test
    fun `an absolute override is used as is`() {
        val absolute = File("/var/cache/fonts").absoluteFile
        assertEquals(absolute, fontCacheDirectory(absolute.path, root, gradleUserHome))
    }
}
