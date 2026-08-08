package com.github.bleeding182.iconbanner.font

import com.github.bleeding182.iconbanner.api.FontSpec
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GoogleFontProviderTest {

    private val robotoMonoBold = FontSpec(family = "Roboto Mono", weight = 700, italic = false)

    @Test
    fun `downloads the resolved font into the cache`(@TempDir cacheDir: File) {
        server().use { server ->
            val file = provider(cacheDir, server).resolve(robotoMonoBold)

            assertTrue(file.isFile, "expected a font file at $file")
            assertContentEquals(fontFixture, file.readBytes())
            assertTrue(
                file.canonicalPath.startsWith(cacheDir.canonicalPath),
                "font landed outside the cache directory: $file",
            )
            assertEquals(1, server.cssRequests.size)
            assertEquals(1, server.fontRequests.size)
        }
    }

    @Test
    fun `asks for the plain weight with a legacy user agent`(@TempDir cacheDir: File) {
        server().use { server ->
            provider(cacheDir, server).resolve(robotoMonoBold)

            val request = server.cssRequests.single()
            assertEquals("family=Roboto+Mono:wght@700", request.query)
            assertEquals("Mozilla/4.0", request.userAgent)
            assertEquals("Mozilla/4.0", server.fontRequests.single().userAgent)
        }
    }

    @Test
    fun `asks for italic on the ital axis`(@TempDir cacheDir: File) {
        server().use { server ->
            provider(cacheDir, server).resolve(robotoMonoBold.copy(italic = true))

            assertEquals("family=Roboto+Mono:ital,wght@1,700", server.cssRequests.single().query)
        }
    }

    @Test
    fun `carries a non-default weight through`(@TempDir cacheDir: File) {
        server().use { server ->
            provider(cacheDir, server).resolve(FontSpec("Open Sans", weight = 300, italic = false))

            assertEquals("family=Open+Sans:wght@300", server.cssRequests.single().query)
        }
    }

    @Test
    fun `a second call for the same spec makes no requests at all`(@TempDir cacheDir: File) {
        server().use { server ->
            val provider = provider(cacheDir, server)
            val first = provider.resolve(robotoMonoBold)
            val requestsAfterFirst = server.snapshot().size

            val second = provider.resolve(robotoMonoBold)

            assertEquals(first, second)
            assertEquals(requestsAfterFirst, server.snapshot().size, "a cache hit must not touch the network")
        }
    }

    @Test
    fun `a cache warmed by another project is used without any request`(@TempDir cacheDir: File) {
        server().use { server ->
            val warmed = provider(cacheDir, server).resolve(robotoMonoBold)
            val requestsAfterWarming = server.snapshot().size

            // A different provider instance, as a second project's build would have.
            val reused = provider(cacheDir, server).resolve(robotoMonoBold)

            assertEquals(warmed, reused)
            assertEquals(requestsAfterWarming, server.snapshot().size)
        }
    }

    @Test
    fun `offline with a cold cache names the url it needed`(@TempDir cacheDir: File) {
        server().use { server ->
            val failure = assertFailsWith<FontResolutionException> {
                provider(cacheDir, server, offline = true).resolve(robotoMonoBold)
            }

            assertContains(failure.message!!, "${server.cssEndpoint}?family=Roboto+Mono:wght@700")
            assertContains(failure.message!!, "offline")
            assertEquals(0, server.snapshot().size, "offline mode must not make requests")
        }
    }

    @Test
    fun `offline with a warm cache succeeds`(@TempDir cacheDir: File) {
        server().use { server ->
            val warmed = provider(cacheDir, server).resolve(robotoMonoBold)

            val offline = provider(cacheDir, server, offline = true).resolve(robotoMonoBold)

            assertEquals(warmed, offline)
            assertContentEquals(fontFixture, offline.readBytes())
        }
    }

    @Test
    fun `an unavailable weight is reported with the family and the response`(@TempDir cacheDir: File) {
        server().use { server ->
            server.css = { _, _ -> 400 to googleErrorPage.toByteArray(UTF_8) }

            val failure = assertFailsWith<FontResolutionException> {
                provider(cacheDir, server).resolve(robotoMonoBold)
            }

            val message = failure.message!!
            assertContains(message, "'Roboto Mono'")
            assertContains(message, "weight 700")
            assertContains(message, "HTTP 400")
            // The body is included, but stripped of the ten kilobytes of script wrapped around it.
            assertContains(message, "Missing font family")
            assertTrue("<script" !in message, "the error page's markup leaked into the message")
        }
    }

    @Test
    fun `a css response without a ttf url fails clearly`(@TempDir cacheDir: File) {
        server().use { server ->
            server.css = { _, _ ->
                200 to """
                    @font-face {
                      font-family: 'Roboto Mono';
                      font-style: normal;
                      font-weight: 700;
                      src: url(https://fonts.gstatic.com/s/robotomono/v31/whatever.woff2) format('woff2');
                    }
                """.trimIndent().toByteArray(UTF_8)
            }

            val failure = assertFailsWith<FontResolutionException> {
                provider(cacheDir, server).resolve(robotoMonoBold)
            }

            assertContains(failure.message!!, "no TrueType (.ttf) URL")
            assertContains(failure.message!!, "'Roboto Mono' weight 700")
        }
    }

    @Test
    fun `a substituted weight is refused rather than silently used`(@TempDir cacheDir: File) {
        server().use { server ->
            // What the live endpoint does for a weight a variable family has no instance for.
            server.css = { _, fontUrl ->
                200 to """
                    @font-face {
                      font-family: 'Roboto Mono';
                      font-style: normal;
                      font-weight: 100;
                      src: url($fontUrl) format('truetype');
                    }
                    @font-face {
                      font-family: 'Roboto Mono';
                      font-style: normal;
                      font-weight: 200;
                      src: url($fontUrl) format('truetype');
                    }
                """.trimIndent().toByteArray(UTF_8)
            }

            val failure = assertFailsWith<FontResolutionException> {
                provider(cacheDir, server).resolve(robotoMonoBold)
            }

            assertContains(failure.message!!, "did not offer 'Roboto Mono' weight 700")
            assertEquals(0, server.fontRequests.size, "nothing should have been downloaded")
        }
    }

    @Test
    fun `an html error page is rejected and not left in the cache`(@TempDir cacheDir: File) {
        server().use { server ->
            server.font = { 200 to googleErrorPage.toByteArray(UTF_8) }

            val failure = assertFailsWith<FontResolutionException> {
                provider(cacheDir, server).resolve(robotoMonoBold)
            }

            assertContains(failure.message!!, "did not return a TrueType font")
            assertEquals(emptyList(), cachedFileNames(cacheDir), "a non-font was cached")
            assertEquals(emptyList(), leftoverTempFiles(cacheDir))
        }
    }

    @Test
    fun `concurrent resolution of one spec yields a single valid file`(@TempDir cacheDir: File) {
        server().use { server ->
            val threads = 8
            val barrier = CyclicBarrier(threads)
            val pool = Executors.newFixedThreadPool(threads)
            val results = try {
                pool.invokeAll(
                    (1..threads).map {
                        Callable {
                            barrier.await(10, TimeUnit.SECONDS)
                            provider(cacheDir, server).resolve(robotoMonoBold)
                        }
                    },
                ).map { it.get(30, TimeUnit.SECONDS) }
            } finally {
                pool.shutdownNow()
            }

            assertEquals(1, results.distinct().size, "threads disagreed about the cached path")
            results.forEach { assertContentEquals(fontFixture, it.readBytes()) }
            assertEquals(1, cachedFileNames(cacheDir).size)
            assertEquals(emptyList(), leftoverTempFiles(cacheDir))
            // The lock is keyed by cache slot, not by provider instance, so one download serves all.
            assertEquals(1, server.fontRequests.size)
        }
    }

    @Test
    fun `a blank family is rejected before any request`(@TempDir cacheDir: File) {
        server().use { server ->
            val failure = assertFailsWith<FontResolutionException> {
                provider(cacheDir, server).resolve(FontSpec("  ", 400, false))
            }

            assertContains(failure.message!!, "blank")
            assertEquals(0, server.snapshot().size)
        }
    }

    @Test
    fun `the default cache directory sits under the gradle user home`() {
        val path = GoogleFontProvider.defaultCacheDirectory().path.replace(File.separatorChar, '/')

        assertTrue(
            path.endsWith("caches/android-icon-banner/fonts"),
            "unexpected default cache directory: $path",
        )
    }

    private fun server() = FakeFontServer(fontFixture)

    private fun provider(cacheDir: File, server: FakeFontServer, offline: Boolean = false) =
        GoogleFontProvider(
            cacheDirectory = cacheDir,
            offline = offline,
            cssEndpoint = server.cssEndpoint,
        )

    private fun cachedFileNames(cacheDir: File): List<String> {
        val files = cacheDir.toPath().resolve("files")
        if (!Files.isDirectory(files)) return emptyList()
        return Files.list(files).use { stream ->
            stream.map { it.fileName.toString() }.filter { it.endsWith(".ttf") }.sorted().toList()
        }
    }

    private fun leftoverTempFiles(cacheDir: File): List<String> =
        Files.walk(cacheDir.toPath()).use { stream ->
            stream.map { it.fileName.toString() }.filter { it.endsWith(".tmp") }.sorted().toList()
        }

    private fun assertContentEquals(expected: ByteArray, actual: ByteArray) {
        assertEquals(expected.size, actual.size, "byte counts differ")
        assertTrue(expected.contentEquals(actual), "contents differ")
    }

    private companion object {
        val fontFixture: ByteArray = run {
            val stream = assertNotNull(
                GoogleFontProviderTest::class.java.getResourceAsStream("/font/RobotoMono-Bold.ttf"),
                "missing test fixture /font/RobotoMono-Bold.ttf",
            )
            stream.use { it.readBytes() }
        }

        /** Trimmed from a real 400 response, keeping the shape that matters: script around one sentence. */
        val googleErrorPage: String = """
            <!DOCTYPE html><html lang=en><head><script>window['ppConfig'] = {productName: 'x'};
            (function(){'use strict';function k(a){var b=0;return function(){return b<a.length}}})();
            </script><meta charset=utf-8><title>400: Font family not found</title><style>* { margin: 0; }
            </style></head><body><p><b>400:</b>&nbsp;<ins>Missing font family</ins></p>
            <p>The requested font families are not available.
            <p>Requested: Roboto Mono (style: normal, weight: 400, {wght=700.0})</body></html>
        """.trimIndent()
    }
}
