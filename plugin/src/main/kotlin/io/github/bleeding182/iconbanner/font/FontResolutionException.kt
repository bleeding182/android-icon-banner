package io.github.bleeding182.iconbanner.font

/**
 * Every way the font layer can fail, with an actionable message. Unchecked on purpose: the
 * provider interface names no exception, and the Gradle layer turns this into a build failure.
 */
internal class FontResolutionException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
