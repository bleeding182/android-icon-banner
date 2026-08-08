package com.github.bleeding182.iconbanner.font

import com.github.bleeding182.iconbanner.api.FontSpec

/** One `@font-face` block of a Google Fonts CSS response. Every field is optional in principle. */
internal data class FontFace(
    val style: String?,
    val weight: String?,
    val unicodeRange: String?,
    val ttfUrl: String?,
)

/**
 * Parses the CSS the Google Fonts `css2` endpoint returns to a legacy user agent.
 *
 * A legacy UA gets TrueType rather than woff2, and — verified against the live endpoint — a single
 * unsubsetted `@font-face` block. Subsetted responses (several blocks with `unicode-range`) are
 * still handled, because nothing in the contract promises they cannot happen.
 */
internal object GoogleFontsCss {

    private val FONT_FACE = Regex("""@font-face\s*\{([^}]*)}""", RegexOption.IGNORE_CASE)
    private val TTF_URL = Regex("""url\(\s*["']?([^"')\s]+\.ttf)["']?\s*\)""", RegexOption.IGNORE_CASE)

    fun parse(css: String): List<FontFace> = FONT_FACE.findAll(css)
        .map { match ->
            val body = match.groupValues[1]
            FontFace(
                style = declaration(body, "font-style"),
                weight = declaration(body, "font-weight"),
                unicodeRange = declaration(body, "unicode-range"),
                ttfUrl = TTF_URL.find(body)?.groupValues?.get(1),
            )
        }
        .toList()

    private fun declaration(body: String, name: String): String? =
        Regex("""(?:^|;)\s*$name\s*:\s*([^;]+)""", RegexOption.IGNORE_CASE)
            .find(body)
            ?.groupValues
            ?.get(1)
            ?.trim()

    /**
     * Picks the face that actually answers [spec].
     *
     * The weight is verified rather than trusted. Asking a variable family for a weight that is not
     * one of its named instances returns HTTP 200 with the neighbouring instances instead — silently
     * accepting the first would ship a banner in the wrong weight, which is exactly the "substituted
     * a different face" failure the design rules out.
     */
    fun selectTtfUrl(css: String, spec: FontSpec, requestUrl: String): String {
        val faces = parse(css).filter { !it.ttfUrl.isNullOrBlank() }
        if (faces.isEmpty()) {
            throw FontResolutionException(
                "The Google Fonts response for ${spec.describe()} contained no TrueType (.ttf) URL. " +
                    "Requested $requestUrl. Response: ${summarizeBody(css)}",
            )
        }

        val matching = faces.filter { it.matchesStyle(spec) && it.matchesWeight(spec) }
        if (matching.isEmpty()) {
            val offered = faces.joinToString(", ") { face ->
                "${face.style ?: "?"} ${face.weight ?: "?"}"
            }
            throw FontResolutionException(
                "Google Fonts did not offer ${spec.describe()}. Requested $requestUrl, which returned " +
                    "these faces instead: $offered. Not every family provides every weight — pick one " +
                    "the family actually ships.",
            )
        }

        // An unsubsetted face covers every glyph; prefer it. Otherwise take the last subset, which is
        // the order the endpoint uses to put latin last.
        val chosen = matching.firstOrNull { it.unicodeRange == null } ?: matching.last()
        return chosen.ttfUrl!!
    }

    private fun FontFace.matchesStyle(spec: FontSpec): Boolean {
        val declared = style?.lowercase() ?: return true
        return if (spec.italic) declared.startsWith("italic") || declared.startsWith("oblique")
        else declared.startsWith("normal")
    }

    private fun FontFace.matchesWeight(spec: FontSpec): Boolean {
        val declared = weight ?: return true
        val numbers = Regex("""\d+""").findAll(declared).mapNotNull { it.value.toIntOrNull() }.toList()
        return when (numbers.size) {
            0 -> true // a keyword such as `bold`; nothing useful to check against
            1 -> numbers[0] == spec.weight
            else -> spec.weight >= numbers.min() && spec.weight <= numbers.max() // variable range
        }
    }
}

/** `'Roboto Mono' weight 700` / `'Roboto Mono' weight 700 italic`, for error messages. */
internal fun FontSpec.describe(): String =
    "'$family' weight $weight" + if (italic) " italic" else ""

/**
 * Condenses a response body for an error message.
 *
 * The CSS endpoint answers an unavailable family or weight with a full HTML error page: ten
 * kilobytes of inlined script wrapped around one useful sentence. Pasting that verbatim into a build
 * failure buries the cause, so scripts, styles and tags are stripped first.
 */
internal fun summarizeBody(body: String, limit: Int = 400): String {
    val text = if (body.contains("<html", ignoreCase = true) || body.contains("<!DOCTYPE", ignoreCase = true)) {
        body
            .replace(Regex("""<script\b[^>]*>.*?</script>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("""<style\b[^>]*>.*?</style>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
    } else {
        body
    }
    val collapsed = text.replace(Regex("""\s+"""), " ").trim()
    return when {
        collapsed.isEmpty() -> "<empty>"
        collapsed.length <= limit -> collapsed
        else -> collapsed.take(limit) + "… (truncated)"
    }
}
