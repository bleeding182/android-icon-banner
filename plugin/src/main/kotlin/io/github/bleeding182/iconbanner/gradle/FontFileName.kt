package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.api.FontSpec
import java.util.Locale

/**
 * The name a face is stored under, e.g. `roboto-mono-700.ttf`.
 *
 * Named after the face, not the banner, so two banners sharing a face share one file.
 */
internal fun fontFileName(spec: FontSpec): String {
    val family = spec.family.slug().ifEmpty { "font" }
    val slant = if (spec.italic) "-italic" else ""
    return "$family-${spec.weight}$slant.ttf"
}

/** ASCII rather than [Char.isLetterOrDigit], so the name survives any filesystem encoding. */
private fun String.slug(): String = buildString {
    for (character in this@slug.lowercase(Locale.ROOT)) {
        when {
            character in 'a'..'z' || character in '0'..'9' -> append(character)
            isEmpty() || last() == '-' -> Unit
            else -> append('-')
        }
    }
}.trimEnd('-')
