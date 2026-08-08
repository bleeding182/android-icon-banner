package io.github.bleeding182.iconbanner.font

import io.github.bleeding182.iconbanner.api.FontProvider
import io.github.bleeding182.iconbanner.api.FontSpec
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches a Google Font as a TrueType file and caches it for every project on the machine.
 *
 * Two steps, both at execution time. The CSS endpoint is asked for the family and axes; it answers
 * with a direct `.ttf` URL, which is then downloaded. No API key is involved.
 *
 * A repeat request for the same [FontSpec] touches the network zero times — not even for the CSS —
 * because the resolved URL is cached alongside the font. See [FontCache] for the layout.
 *
 * @param cacheDirectory root of the shared cache. Defaults to the Gradle user home so the download
 *   is paid once per machine and survives `clean`; always pass an explicit directory in tests.
 * @param offline serve from the cache or fail, naming the URL that would have been fetched.
 * @param cssEndpoint the Google Fonts CSS endpoint. Injectable so tests can point at a local server.
 *   The font URL itself is not configurable: it comes out of the CSS response body, so a test server
 *   controls it by emitting its own address.
 * @param fontOrigin the only origin a font may be downloaded from, as `scheme://host[:port]`. The
 *   `.ttf` URL is lifted out of a response body and can also come back out of a cache file written
 *   by an earlier build, so neither is trustworthy input. Injectable only so the loopback test
 *   server can stand in for gstatic.
 * @param userAgent sent on both requests. See [USER_AGENT].
 */
internal class GoogleFontProvider @JvmOverloads constructor(
    val cacheDirectory: File = defaultCacheDirectory(),
    private val offline: Boolean = false,
    private val cssEndpoint: String = GOOGLE_FONTS_CSS_ENDPOINT,
    private val fontOrigin: String = GOOGLE_FONTS_FILE_ORIGIN,
    private val userAgent: String = USER_AGENT,
    private val httpClient: HttpClient = defaultHttpClient(),
) : FontProvider {

    private val cache = FontCache(cacheDirectory.toPath())

    override fun resolve(spec: FontSpec): File {
        validate(spec)
        // Keyed by cache slot rather than by instance: the Gradle layer is free to build a provider
        // per task, and two tasks in one daemon should still download a font only once.
        val lock = locks.computeIfAbsent(cacheDirectory.absolutePath + "|" + FontCache.specKey(spec)) { Any() }
        synchronized(lock) {
            val knownUrl = cache.resolvedUrl(spec)?.also(::checkOrigin)
            if (knownUrl != null) {
                cache.cachedFont(knownUrl)?.let { return it.toFile() }
            }

            val cssUrl = cssUrl(spec)
            if (offline) throw offlineFailure(spec, cssUrl, knownUrl)

            val fontUrl = knownUrl ?: GoogleFontsCss.selectTtfUrl(fetchCss(cssUrl, spec), spec, cssUrl)
            checkOrigin(fontUrl)
            val file = download(fontUrl)
            cache.recordResolvedUrl(spec, fontUrl)
            return file.toFile()
        }
    }

    private fun validate(spec: FontSpec) {
        if (spec.family.isBlank()) {
            throw FontResolutionException("The banner font family is blank. Name a Google Font, e.g. \"Roboto Mono\".")
        }
        if (spec.weight !in 1..1000) {
            throw FontResolutionException(
                "Font weight ${spec.weight} for '${spec.family}' is outside the CSS range 1..1000. " +
                    "Use a normal weight such as 400 or 700.",
            )
        }
    }

    /**
     * Refuses to fetch anything that is not served from [fontOrigin].
     *
     * The URL reaching here was lifted out of a `url(...)` in a response body, or read back out of a
     * plain text file in the shared cache — a cleartext `http://elsewhere/x.ttf` in either place
     * would otherwise be downloaded and handed to the glyph outliner. Scheme, host and port are
     * compared individually rather than as one string so `https://fonts.gstatic.com@elsewhere/x.ttf`,
     * whose *authority* starts with the expected host, is rejected too.
     */
    private fun checkOrigin(fontUrl: String) {
        val uri = try {
            URI(fontUrl)
        } catch (e: URISyntaxException) {
            throw FontResolutionException("Not a usable font URL: $fontUrl", e)
        }
        val expected = URI(fontOrigin)
        val allowed = uri.userInfo == null &&
            uri.scheme.equals(expected.scheme, ignoreCase = true) &&
            uri.host.equals(expected.host, ignoreCase = true) &&
            uri.port == expected.port
        if (!allowed) {
            throw FontResolutionException(
                "The banner font would have been downloaded from $fontUrl, which is not $fontOrigin. " +
                    "Google Fonts serves font files from that one origin over https; refusing to " +
                    "fetch from anywhere else. Delete ${cacheDirectory.absolutePath} if a stale " +
                    "cache entry is the cause.",
            )
        }
    }

    /**
     * `…/css2?family=Roboto+Mono:wght@700`, or `…?family=Roboto+Mono:ital,wght@1,700` when italic.
     *
     * The axis list and the value tuple have to line up, which is why italic changes both halves.
     * Only the family is percent-encoded; the `:`, `@` and `,` are structural and stay literal.
     */
    internal fun cssUrl(spec: FontSpec): String {
        val family = URLEncoder.encode(spec.family.trim().replace(Regex("""\s+"""), " "), UTF_8)
        val axes = if (spec.italic) "ital,wght@1,${spec.weight}" else "wght@${spec.weight}"
        return "$cssEndpoint?family=$family:$axes"
    }

    private fun offlineFailure(spec: FontSpec, cssUrl: String, knownUrl: String?): FontResolutionException {
        val missing = knownUrl ?: cssUrl
        val what = if (knownUrl != null) {
            "The font file for ${spec.describe()} is not in the cache, and the build is offline."
        } else {
            "${spec.describe()} has never been resolved on this machine, and the build is offline."
        }
        return FontResolutionException(
            "$what Warm the cache at ${cacheDirectory.absolutePath} with one online build, or fetch " +
                "$missing yourself.",
        )
    }

    private fun fetchCss(cssUrl: String, spec: FontSpec): String {
        val response = send(cssUrl, HttpResponse.BodyHandlers.ofString(UTF_8))
        val status = response.statusCode()
        if (status in 400..499) {
            throw FontResolutionException(
                "Google Fonts does not offer ${spec.describe()}. $cssUrl returned HTTP $status. " +
                    "Response: ${summarizeBody(response.body())}",
            )
        }
        if (status != 200) {
            throw FontResolutionException(
                "Fetching $cssUrl failed with HTTP $status. Response: ${summarizeBody(response.body())}",
            )
        }
        return response.body()
    }

    private fun download(fontUrl: String) = run {
        val response = send(fontUrl, HttpResponse.BodyHandlers.ofInputStream())
        response.body().use { body ->
            if (response.statusCode() != 200) {
                throw FontResolutionException(
                    "Downloading the font from $fontUrl failed with HTTP ${response.statusCode()}.",
                )
            }
            cache.store(fontUrl, body)
        }
    }

    private fun <T> send(url: String, handler: HttpResponse.BodyHandler<T>): HttpResponse<T> {
        val request = try {
            HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", userAgent)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build()
        } catch (e: IllegalArgumentException) {
            throw FontResolutionException("Not a usable URL: $url", e)
        }
        try {
            return httpClient.send(request, handler)
        } catch (e: IOException) {
            throw FontResolutionException("Could not reach $url: ${e.message}", e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw FontResolutionException("Interrupted while fetching $url", e)
        }
    }

    companion object {
        const val GOOGLE_FONTS_CSS_ENDPOINT: String = "https://fonts.googleapis.com/css2"

        /** The single origin Google Fonts serves `.ttf` files from. */
        const val GOOGLE_FONTS_FILE_ORIGIN: String = "https://fonts.gstatic.com"

        /**
         * Identifies the plugin, honestly.
         *
         * The `css2` endpoint picks a font format from the user agent, and it only serves woff2 to
         * agents it *recognises* as modern browsers. Anything it does not recognise — this string,
         * curl's default, an empty header — gets TrueType, which is what the plugin wants, since a
         * woff2 would drag a brotli decoder in for no benefit. Verified against the live endpoint.
         *
         * So there is nothing to gain by claiming to be an ancient browser, which an earlier version
         * of this did. Adding a *recognised* browser string is the only change that would break the
         * format assumption.
         */
        const val USER_AGENT: String =
            "android-icon-banner (+https://github.com/bleeding182/android-icon-banner)"

        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(30)

        private val locks = ConcurrentHashMap<String, Any>()

        /** `<gradle user home>/caches/android-icon-banner/fonts`, honouring an overridden home. */
        fun defaultCacheDirectory(): File {
            val gradleUserHome = System.getProperty("gradle.user.home")
                ?: System.getenv("GRADLE_USER_HOME")
                ?: File(System.getProperty("user.home"), ".gradle").path
            return File(gradleUserHome, "caches/android-icon-banner/fonts")
        }

        fun defaultHttpClient(): HttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }
}
