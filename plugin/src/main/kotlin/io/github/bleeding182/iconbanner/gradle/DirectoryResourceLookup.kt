package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.api.ResourceLookup
import io.github.bleeding182.iconbanner.api.ResourceRef
import io.github.bleeding182.iconbanner.api.SourceResource
import java.io.File

/**
 * [ResourceLookup] over a variant's static resource roots.
 *
 * [roots] must be highest priority first, which is the order AGP's `sources.res.static` gives.
 */
internal class DirectoryResourceLookup(private val roots: List<File>) : ResourceLookup {

    override fun find(ref: ResourceRef): List<SourceResource> {
        val winners = LinkedHashMap<String, SourceResource>()
        for (root in roots) {
            val folders = root.listFiles()?.filter { it.isDirectory && it.name.resourceType() == ref.type }
                ?: continue
            for (folder in folders.sortedBy { it.name }) {
                val files = folder.listFiles()?.filter { it.isFile && it.resourceName() == ref.name }
                    ?: continue
                for (file in files.sortedBy { it.name }) {
                    // First root to provide this qualifier wins; later roots are lower priority.
                    winners.putIfAbsent(
                        "${folder.name}/${ref.name}",
                        SourceResource(
                            qualifiers = folder.name,
                            fileName = file.name,
                            xml = if (file.extension.equals("xml", ignoreCase = true)) {
                                file.readText()
                            } else {
                                null
                            },
                        ),
                    )
                }
            }
        }
        return winners.values.toList()
    }

    /** `mipmap-anydpi-v26` is of type `mipmap`. */
    private fun String.resourceType(): String = substringBefore('-')

    /** `ic_launcher.xml` and `ic_launcher.9.png` are both the resource `ic_launcher`. */
    private fun File.resourceName(): String = name.substringBefore('.')
}
