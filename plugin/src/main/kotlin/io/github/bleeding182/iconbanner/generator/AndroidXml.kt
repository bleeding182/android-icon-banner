package io.github.bleeding182.iconbanner.generator

import org.w3c.dom.Comment
import org.w3c.dom.Document
import org.w3c.dom.DocumentType
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.ProcessingInstruction
import org.w3c.dom.Text
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** Thrown for XML that cannot be parsed; the generator turns it into a [Failure] message. */
internal class XmlParseException(message: String, cause: Throwable?) : Exception(message, cause)

/**
 * Namespace-aware DOM parsing plus a deterministic serialiser.
 *
 * The output *replaces* the original resource, so anything the parser drops is dropped from the
 * app, and byte-identical input must give byte-identical output or every golden file churns.
 */
internal object AndroidXml {

    const val ANDROID_NS: String = "http://schemas.android.com/apk/res/android"

    private const val INDENT = "    "

    private val factory: DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            // Resource XML never needs a DTD, and fetching one would be a hidden network call.
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }

    fun parse(xml: String, describedAs: String): Document {
        val builder = synchronized(factory) { factory.newDocumentBuilder() }
        // Silence the default handler, which prints parse errors to stderr before throwing.
        builder.setErrorHandler(null)
        return try {
            builder.parse(InputSource(StringReader(xml)))
        } catch (e: Exception) {
            throw XmlParseException("$describedAs is not valid XML: ${e.message}", e)
        }
    }

    /** Serialises [document] to UTF-8 text, indented four spaces, with a trailing newline. */
    fun serialize(document: Document): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        val children = document.childNodes
        for (index in 0 until children.length) {
            appendNode(children.item(index), 0)
        }
    }

    private fun StringBuilder.appendNode(node: Node, depth: Int) {
        when (node) {
            is Element -> appendElement(node, depth)
            is Comment -> {
                indent(depth)
                append("<!--").append(node.data).append("-->\n")
            }

            // Rare, but the output replaces the original wholesale.
            is DocumentType -> {
                append("<!DOCTYPE ").append(node.name)
                node.publicId?.let { append(" PUBLIC \"").append(it).append('"') }
                node.systemId?.let {
                    if (node.publicId == null) append(" SYSTEM")
                    append(" \"").append(it).append('"')
                }
                node.internalSubset?.let { append(" [").append(it).append(']') }
                append(">\n")
            }

            is ProcessingInstruction -> {
                indent(depth)
                append("<?").append(node.target).append(' ').append(node.data).append("?>\n")
            }

            is Text -> {
                val text = node.data
                // Whitespace between elements is layout: drop it and re-indent, so output is canonical.
                if (text.isBlank()) return
                indent(depth)
                append(escapeText(text.trim())).append('\n')
            }
        }
    }

    private fun StringBuilder.appendElement(element: Element, depth: Int) {
        indent(depth)
        append('<').append(element.nodeName)

        val attributes = element.orderedAttributes()
        val onePerLine = attributes.size > 1
        for (attribute in attributes) {
            if (onePerLine) {
                append('\n')
                indent(depth + 1)
            } else {
                append(' ')
            }
            append(attribute.nodeName).append("=\"").append(escapeAttribute(attribute.nodeValue)).append('"')
        }

        val children = (0 until element.childNodes.length)
            .map { element.childNodes.item(it) }
            .filterNot { it is Text && it.data.isBlank() }

        if (children.isEmpty()) {
            append(" />\n")
            return
        }
        append(">\n")
        children.forEach { appendNode(it, depth + 1) }
        indent(depth)
        append("</").append(element.nodeName).append(">\n")
    }

    /** Namespace declarations first, then alphabetically: DOM attribute order is a parser detail. */
    private fun Element.orderedAttributes(): List<Node> =
        (0 until attributes.length)
            .map { attributes.item(it) }
            .sortedWith(compareBy({ if (it.nodeName.startsWith("xmlns")) 0 else 1 }, { it.nodeName }))

    private fun StringBuilder.indent(depth: Int) {
        repeat(depth) { append(INDENT) }
    }

    private fun escapeText(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun escapeAttribute(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("\n", "&#10;")
            .replace("\r", "&#13;")
            .replace("\t", "&#9;")

    /**
     * The prefix bound to the Android namespace on [root], declaring `xmlns:android` if absent.
     * Respects an unusual prefix rather than imposing one.
     */
    fun androidPrefix(root: Element): String {
        val existing = root.lookupPrefix(ANDROID_NS)
        if (existing != null) return existing
        root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:android", ANDROID_NS)
        return "android"
    }
}

/** Direct element children of this node, ignoring text and comments. */
internal fun Node.childElements(): List<Element> =
    (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }

/** The first direct child element with this local name, ignoring prefix. */
internal fun Node.firstChild(localName: String): Element? =
    childElements().firstOrNull { it.localNameOrTag() == localName }

/** Local name for namespace-aware nodes, falling back to the tag name for un-prefixed ones. */
internal fun Element.localNameOrTag(): String = localName ?: tagName

/** Value of an `android:`-namespaced attribute, or null when absent or empty. */
internal fun Element.androidAttribute(name: String): String? =
    getAttributeNS(AndroidXml.ANDROID_NS, name).takeIf { it.isNotEmpty() }

/** Sets an `android:`-namespaced attribute using whatever prefix the document binds. */
internal fun Element.setAndroidAttribute(prefix: String, name: String, value: String) {
    setAttributeNS(AndroidXml.ANDROID_NS, "$prefix:$name", value)
}

/** Creates a namespace-free element, which is what every VectorDrawable tag is. */
internal fun Document.createVectorElement(name: String): Element = createElementNS(null, name)
