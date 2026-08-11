package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.api.ResourceLookup
import io.github.bleeding182.iconbanner.api.ResourceRef
import io.github.bleeding182.iconbanner.api.SourceResource
import org.w3c.dom.Element
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** What `<application android:icon>` and `android:roundIcon` point at. */
internal data class DeclaredIcons(val icon: ResourceRef, val roundIcon: ResourceRef?, val roundIsFallback: Boolean) {

    /**
     * The round icon worth bannering, or null.
     *
     * A declared one is passed through, so a broken declaration still fails loudly. The conventional
     * `ic_launcher_round` is only picked up when it actually exists — in any form, raster included: a
     * launcher that asks for the round variant must not come back with an unmarked icon.
     *
     * "Exists" means a file a banner could go on, not merely a file. A name nobody declared must not be
     * what fails a build, and every file backing it being unbannerable is exactly what the generator
     * fails on: a round icon that is only `ic_launcher_round.9.png` used to be ignored and would
     * otherwise now be a hard error nobody asked for.
     */
    fun roundIconToBanner(resources: ResourceLookup): ResourceRef? {
        val round = roundIcon ?: return null
        if (!roundIsFallback) return round
        return round.takeIf { resources.find(it).any(SourceResource::isBannerable) }
    }
}

/**
 * Reads the launcher icon out of a variant's source manifests, so the plugin needs no DSL property
 * naming it. Parsing happens at execution time, inside the task, to stay configuration-cache safe.
 */
internal object ManifestIcons {

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val DEFAULT_ICON = "@mipmap/ic_launcher"
    private const val DEFAULT_ROUND_ICON = "@mipmap/ic_launcher_round"

    /** [manifests] ordered highest priority first, as AGP's `sources.manifests.all` provides them. */
    fun read(manifests: List<File>): DeclaredIcons {
        var icon: String? = null
        var round: String? = null
        for (manifest in manifests) {
            if (!manifest.isFile) continue
            val application = applicationElement(manifest) ?: continue
            if (icon == null) icon = application.androidAttribute("icon")
            if (round == null) round = application.androidAttribute("roundIcon")
            if (icon != null && round != null) break
        }
        return DeclaredIcons(
            icon = ResourceRef.parse(icon ?: DEFAULT_ICON) ?: ResourceRef.parse(DEFAULT_ICON)!!,
            roundIcon = ResourceRef.parse(round ?: DEFAULT_ROUND_ICON),
            roundIsFallback = round == null,
        )
    }

    private fun applicationElement(manifest: File): Element? {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        }
        val document = manifest.inputStream().buffered().use { factory.newDocumentBuilder().parse(it) }
        val children = document.documentElement?.childNodes ?: return null
        for (index in 0 until children.length) {
            val node = children.item(index)
            if (node is Element && node.tagName == "application") return node
        }
        return null
    }

    private fun Element.androidAttribute(name: String): String? =
        getAttributeNS(ANDROID_NS, name).takeIf { it.isNotEmpty() }
}
