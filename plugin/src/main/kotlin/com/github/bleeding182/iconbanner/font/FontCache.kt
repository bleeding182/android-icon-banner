package com.github.bleeding182.iconbanner.font

import com.github.bleeding182.iconbanner.api.FontSpec
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * The on-disk font cache, shared by every project on the machine.
 *
 * Layout, relative to the cache root:
 *
 * ```
 * urls/<sha256 of the spec>.url     a text file holding the resolved TrueType URL
 * files/<sha256 of that URL>.ttf    the font itself
 * ```
 *
 * Two levels rather than one because the design requires a cache hit to make no network calls at
 * all: the font file is keyed by the URL, but the URL is only known after asking the CSS endpoint.
 * The `urls` mapping is what lets a repeat request skip that question too.
 *
 * Every write lands via a temporary file in the destination directory followed by an atomic move.
 * The cache outlives the builds that write it, so a half-written `.ttf` left behind by an
 * interrupted or interleaved build would poison every later build on the machine.
 */
internal class FontCache(private val root: Path) {

    private val urlsDirectory: Path get() = root.resolve("urls")
    private val filesDirectory: Path get() = root.resolve("files")

    /** The resolved TrueType URL recorded for [spec], or null when it has never been resolved here. */
    fun resolvedUrl(spec: FontSpec): String? {
        val file = urlsDirectory.resolve(specKey(spec) + ".url")
        return try {
            if (!Files.isRegularFile(file)) null else Files.readString(file, UTF_8).trim().ifEmpty { null }
        } catch (_: IOException) {
            null
        }
    }

    fun recordResolvedUrl(spec: FontSpec, url: String) {
        write(urlsDirectory, specKey(spec) + ".url", url.toByteArray(UTF_8))
    }

    /** Where [url]'s font lives, whether or not it has been downloaded. */
    fun fontFile(url: String): Path = filesDirectory.resolve(sha256(url) + ".ttf")

    /**
     * The cached font for [url], or null when it is absent or not a font.
     *
     * The magic-byte check is repeated on read, not just on write: a file that predates this check,
     * or one truncated by a full disk, should re-download rather than fail somewhere far away in the
     * glyph outliner.
     */
    fun cachedFont(url: String): Path? {
        val file = fontFile(url)
        return if (Files.isRegularFile(file) && hasTrueTypeMagic(file)) file else null
    }

    /**
     * Streams [source] into the cache slot for [url], rejecting anything that is not a font.
     *
     * @return the cached file.
     */
    fun store(url: String, source: InputStream): Path {
        val target = fontFile(url)
        Files.createDirectories(filesDirectory)
        val temporary = Files.createTempFile(filesDirectory, "download", ".tmp")
        try {
            Files.newOutputStream(temporary).use { output -> source.copyTo(output) }
            if (!hasTrueTypeMagic(temporary)) {
                throw FontResolutionException(
                    "$url did not return a TrueType font. The first bytes were ${magicOf(temporary)}, " +
                        "which is not 0x00010000, 'true' or 'OTTO' — most likely an error page rather " +
                        "than a font. Nothing was written to the cache.",
                )
            }
            moveIntoPlace(temporary, target)
            return target
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun write(directory: Path, fileName: String, bytes: ByteArray) {
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, "write", ".tmp")
        try {
            Files.write(temporary, bytes)
            moveIntoPlace(temporary, directory.resolve(fileName))
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun moveIntoPlace(temporary: Path, target: Path) {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: FileAlreadyExistsException) {
            // Another build won the race with identical content. Its file is as good as ours.
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        /** Identifies a face independently of how the user spelled the family. */
        fun specKey(spec: FontSpec): String = sha256(
            listOf(
                spec.family.trim().replace(Regex("""\s+"""), " ").lowercase(),
                spec.weight.toString(),
                spec.italic.toString(),
            ).joinToString("|"),
        )

        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

        /**
         * True when [file] starts with a recognised sfnt signature: `0x00010000` (TrueType), `true`
         * (a legacy Apple variant) or `OTTO` (CFF outlines).
         */
        fun hasTrueTypeMagic(file: Path): Boolean {
            val magic = readMagic(file) ?: return false
            return magic.contentEquals(byteArrayOf(0x00, 0x01, 0x00, 0x00)) ||
                magic.contentEquals("true".toByteArray(UTF_8)) ||
                magic.contentEquals("OTTO".toByteArray(UTF_8))
        }

        private fun magicOf(file: Path): String =
            readMagic(file)?.joinToString(" ") { "0x%02x".format(it) } ?: "<fewer than four bytes>"

        private fun readMagic(file: Path): ByteArray? = try {
            Files.newInputStream(file).use { input ->
                val magic = ByteArray(4)
                if (input.readNBytes(magic, 0, 4) == 4) magic else null
            }
        } catch (_: IOException) {
            null
        }
    }
}
