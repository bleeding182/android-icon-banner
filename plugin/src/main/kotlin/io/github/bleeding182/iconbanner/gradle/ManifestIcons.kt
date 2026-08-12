package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.api.ResourceRef
import org.gradle.api.GradleException
import org.w3c.dom.Element
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * What `<application android:icon>` and `android:roundIcon` point at, or null for an attribute the
 * manifests do not declare.
 *
 * Nothing is assumed on their behalf. An attribute nobody declared names no icon, and the plugin only
 * ever modifies an icon the app actually has: Android populates `roundIconRes` from `android:roundIcon`
 * alone, so a `mipmap/ic_launcher_round` no manifest points at is artwork no launcher ever loads.
 */
internal data class DeclaredIcons(val icon: ResourceRef?, val roundIcon: ResourceRef?)

/**
 * Reads the launcher icon out of a variant's source manifests, so the plugin needs no DSL property
 * naming it. Parsing happens at execution time, inside the task, to stay configuration-cache safe.
 */
internal object ManifestIcons {

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    /** [manifests] ordered highest priority first, as AGP's `sources.manifests.all` provides them. */
    fun read(manifests: List<File>, variantName: String): DeclaredIcons {
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
            icon = icon?.let { reference(it, "icon", variantName) },
            roundIcon = round?.let { reference(it, "roundIcon", variantName) },
        )
    }

    /** A declaration that cannot be parsed is the user's own, so it fails rather than being ignored. */
    private fun reference(raw: String, attribute: String, variantName: String): ResourceRef =
        ResourceRef.parse(raw)
            ?: throw GradleException(
                "icon banner ($variantName): <application android:$attribute=\"$raw\"> is not a " +
                    "resource reference, so the icon to banner cannot be resolved."
            )

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
