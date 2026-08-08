package io.github.bleeding182.iconbanner.font

import io.github.bleeding182.iconbanner.api.FontSpec
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
 * ```
 * urls/<sha256 of the spec>.url    the resolved TrueType URL
 * files/<sha256 of the url>.ttf    the font itself
 * ```
 *
 * Two levels because the CSS lookup and the download fail independently, and because several
 * specs can resolve to one file. Writes move a temp file into place, so a killed build cannot
 * leave a truncated font behind.
 */
internal class FontCache(private val root: Path) {

    private val urlsDirectory: Path get() = root.resolve("urls")
    private val filesDirectory: Path get() = root.resolve("files")

    /** The URL recorded for [spec], or null. A string off disk, so the caller re-validates its origin. */
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
     * The cached font for [url], or null when absent or not a font. The magic-byte check is repeated
     * on read, since a file may predate a stricter check.
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

        /** A recognised sfnt signature: `0x00010000`, `true` (legacy Apple) or `OTTO` (CFF outlines). */
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
