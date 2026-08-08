package com.github.bleeding182.iconbanner

import com.github.bleeding182.iconbanner.api.BannerGenerator
import com.github.bleeding182.iconbanner.api.BannerRequest
import com.github.bleeding182.iconbanner.api.BannerStyle
import com.github.bleeding182.iconbanner.api.FontProvider
import com.github.bleeding182.iconbanner.api.FontSpec
import com.github.bleeding182.iconbanner.api.GenerationResult
import com.github.bleeding182.iconbanner.api.ResourceRef
import java.io.File
import java.util.Locale

// TEMPORARY. The real generator and font downloader are being written in parallel; these exist only
// so the Gradle layer can be wired and tested end to end. Delete this whole file at integration.

/**
 * TEMPORARY stand-in for the real generator. Follows the icon to its foreground vector and appends
 * one obvious corner triangle, so the wiring can be seen working on a launcher. No text, no
 * monochrome handling, no geometry worth reviewing.
 */
internal class StubBannerGenerator : BannerGenerator {

    override fun generate(request: BannerRequest): GenerationResult {
        val files = LinkedHashMap<String, String>()
        val info = mutableListOf<String>()

        for (ref in listOfNotNull(request.icon, request.roundIcon)) {
            val sources = request.resources.find(ref)
            if (sources.isEmpty()) {
                return GenerationResult.Failure("Icon resource $ref was not found in any resource directory.")
            }
            for (source in sources) {
                val xml = source.xml ?: continue // raster mipmaps are silently skipped
                if (xml.contains("<vector")) {
                    files[source.relativePath] = banner(xml, request.style)
                    info += "banner added to ${source.relativePath}"
                    continue
                }
                for (target in foregroundRefs(xml)) {
                    for (layer in request.resources.find(target)) {
                        val layerXml = layer.xml ?: continue
                        if (!layerXml.contains("<vector")) continue
                        files[layer.relativePath] = banner(layerXml, request.style)
                        info += "banner added to ${layer.relativePath}"
                    }
                }
            }
        }

        if (files.isEmpty()) {
            return GenerationResult.Failure("Found no vector drawable to banner for ${request.icon}.")
        }
        return GenerationResult.Success(files, info)
    }

    private fun foregroundRefs(xml: String): List<ResourceRef> =
        Regex("<foreground[^>]*android:drawable=\"([^\"]+)\"").findAll(xml)
            .mapNotNull { ResourceRef.parse(it.groupValues[1]) }
            .toList()

    private fun banner(xml: String, style: BannerStyle): String {
        val width = attribute(xml, "viewportWidth") ?: 24.0
        val height = attribute(xml, "viewportHeight") ?: width
        val path = buildString {
            // The real generator draws the text as glyph outlines. The stub records it in a comment
            // so the Gradle layer's tests have something to assert the resolved text against.
            append("    <!-- iconBanner text: ").append(style.text).append(" -->\n")
            append("    <path android:fillColor=\"").append(style.color).append("\" android:pathData=\"")
            append("M0,0 L").append(format(width / 2)).append(",0 L0,").append(format(height / 2)).append(" Z")
            append("\" />\n")
        }
        val close = xml.lastIndexOf("</vector>")
        return if (close < 0) xml else xml.substring(0, close) + path + xml.substring(close)
    }

    private fun attribute(xml: String, name: String): Double? =
        Regex("android:$name=\"([0-9.]+)").find(xml)?.groupValues?.get(1)?.toDoubleOrNull()

    /** Root locale, always: a comma decimal separator silently corrupts every generated icon. */
    private fun format(value: Double): String = String.format(Locale.ROOT, "%.2f", value)
}

/**
 * TEMPORARY stand-in for the real font downloader. Writes a deterministic placeholder into the
 * shared cache so the task graph, the caching and the offline flag can all be exercised without a
 * network call. [StubBannerGenerator] never opens the file.
 */
internal class StubFontProvider(private val cacheDir: File, private val offline: Boolean) : FontProvider {

    override fun resolve(spec: FontSpec): File {
        val name = buildString {
            append(spec.family.replace(Regex("[^A-Za-z0-9]"), "_"))
            append('-').append(spec.weight)
            if (spec.italic) append("-italic")
            append(".ttf")
        }
        val target = File(cacheDir, name)
        if (!target.isFile) {
            cacheDir.mkdirs()
            val temporary = File(cacheDir, "$name.${ProcessHandle.current().pid()}.tmp")
            temporary.writeText("stub font placeholder for $spec (offline=$offline)")
            temporary.renameTo(target)
        }
        return target
    }
}
