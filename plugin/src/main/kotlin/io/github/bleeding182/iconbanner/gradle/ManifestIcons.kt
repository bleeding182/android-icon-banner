package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.api.ResourceLookup
import io.github.bleeding182.iconbanner.api.ResourceRef
import org.w3c.dom.Element
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** What `<application android:icon>` and `android:roundIcon` point at. */
internal data class DeclaredIcons(val icon: ResourceRef, val roundIcon: ResourceRef?, val roundIsFallback: Boolean) {

    /**
     * The round icon actually worth bannering, or null.
     *
     * A declared one is passed through whatever it resolves to, so a broken declaration still fails
     * loudly. An *invented* one — [roundIsFallback], nobody wrote `android:roundIcon` — is only a
     * naming convention, and the plugin must not fail over a resource it made up itself. It is kept
     * only when it resolves to at least one XML file, which is exactly the case the generator can
     * handle.
     *
     * "Resolves to nothing" is not a strong enough test. An app with legacy per-density
     * `ic_launcher_round.webp` files but no `mipmap-anydpi-v26/ic_launcher_round.xml` resolves
     * non-empty, and the generator would then fail the build with "only raster files were found" over
     * a resource nobody asked to be bannered — while the documented policy is that rasters are
     * skipped in silence.
     */
    fun roundIconToBanner(resources: ResourceLookup): ResourceRef? {
        val round = roundIcon ?: return null
        if (!roundIsFallback) return round
        return round.takeIf { resources.find(it).any { source -> source.xml != null } }
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
