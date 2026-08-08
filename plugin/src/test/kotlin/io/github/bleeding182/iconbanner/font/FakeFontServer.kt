package io.github.bleeding182.iconbanner.font

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Collections
import java.util.concurrent.Executors

/**
 * A stand-in for `fonts.googleapis.com` and `fonts.gstatic.com` on an ephemeral loopback port.
 *
 * The suite must never touch the real service, and the interesting assertions are about what the
 * provider *sent* — the user agent and the axis syntax — so every request is recorded.
 */
internal class FakeFontServer(private val fontBytes: ByteArray) : AutoCloseable {

    data class Recorded(val path: String, val query: String?, val userAgent: String?)

    private val server: HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    private val executor = Executors.newFixedThreadPool(8)

    val requests: MutableList<Recorded> = Collections.synchronizedList(mutableListOf<Recorded>())

    /** Answers the CSS endpoint. Receives the raw query and the address of the font this server serves. */
    var css: (query: String?, fontUrl: String) -> Pair<Int, ByteArray> = { query, fontUrl ->
        200 to fontFaceCss(query, fontUrl).toByteArray(UTF_8)
    }

    /** Answers the font download. */
    var font: () -> Pair<Int, ByteArray> = { 200 to fontBytes }

    val baseUrl: String get() = "http://${server.address.hostString}:${server.address.port}"
    val cssEndpoint: String get() = "$baseUrl$CSS_PATH"
    val fontUrl: String get() = "$baseUrl$FONT_PATH"

    val cssRequests: List<Recorded> get() = snapshot().filter { it.path == CSS_PATH }
    val fontRequests: List<Recorded> get() = snapshot().filter { it.path == FONT_PATH }

    init {
        server.createContext(CSS_PATH) { exchange ->
            record(exchange)
            val (status, body) = css(exchange.requestURI.rawQuery, fontUrl)
            respond(exchange, status, "text/css; charset=utf-8", body)
        }
        server.createContext(FONT_PATH) { exchange ->
            record(exchange)
            val (status, body) = font()
            respond(exchange, status, "font/ttf", body)
        }
        server.executor = executor
        server.start()
    }

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
    }

    fun snapshot(): List<Recorded> = synchronized(requests) { requests.toList() }

    private fun record(exchange: HttpExchange) {
        requests += Recorded(
            path = exchange.requestURI.path,
            query = exchange.requestURI.rawQuery,
            userAgent = exchange.requestHeaders.getFirst("User-Agent"),
        )
    }

    private fun respond(exchange: HttpExchange, status: Int, contentType: String, body: ByteArray) {
        exchange.responseHeaders.add("Content-Type", contentType)
        exchange.sendResponseHeaders(status, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    companion object {
        const val CSS_PATH = "/css2"
        const val FONT_PATH = "/s/robotomono/v31/font.ttf"

        /** Mirrors the live endpoint: echoes back the requested style and weight, one unsubsetted face. */
        fun fontFaceCss(query: String?, fontUrl: String): String {
            val request = (query ?: "").substringAfter("family=")
            val family = request.substringBefore(':').replace('+', ' ')
            val axes = request.substringAfter(':', "")
            val values = axes.substringAfter('@', "").split(',')
            val italic = axes.startsWith("ital") && values.firstOrNull() == "1"
            return """
                @font-face {
                  font-family: '$family';
                  font-style: ${if (italic) "italic" else "normal"};
                  font-weight: ${values.last()};
                  src: url($fontUrl) format('truetype');
                }
            """.trimIndent()
        }
    }
}
