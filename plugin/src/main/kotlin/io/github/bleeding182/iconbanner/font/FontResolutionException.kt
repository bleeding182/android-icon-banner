package io.github.bleeding182.iconbanner.font

/**
 * Every way the font layer can fail, as one exception type with an actionable message.
 *
 * Unchecked on purpose: [io.github.bleeding182.iconbanner.api.FontProvider.resolve] declares no
 * checked exception, and the Gradle layer only ever wants to surface the message as a build
 * failure.
 */
internal class FontResolutionException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
