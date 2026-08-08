package io.github.bleeding182.iconbanner.gradle

/**
 * Colour values are passed through to the generated `android:fillColor` untouched, so resource and
 * theme references keep working. Only a literal that Android itself could not parse is rejected.
 */
internal object ColorFormat {

    private val HEX = Regex("#(?:[0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})")

    /** Returns [value] unchanged, or throws with a message naming [property] and [variantName]. */
    fun check(value: String, property: String, variantName: String): String {
        val valid = when {
            value.startsWith("#") -> HEX.matches(value)
            // `?colorPrimary` is as legal as `?attr/colorPrimary`; `@colorPrimary` is not.
            value.startsWith("?") -> nameOf(value).isNotEmpty()
            value.startsWith("@") -> typeOf(value).isNotEmpty() && nameOf(value).isNotEmpty()
            else -> false
        }
        require(valid) {
            "iconBanner.$property for variant '$variantName' is '$value', which is not a colour. " +
                "Use a hex literal (#RGB, #ARGB, #RRGGBB, #AARRGGBB), a colour resource " +
                "(@color/name) or a theme attribute (?attr/name)."
        }
        return value
    }

    private fun body(reference: String): String = reference.drop(1).removePrefix("+").substringAfter(':')

    private fun typeOf(reference: String): String =
        body(reference).substringBefore('/', missingDelimiterValue = "")

    private fun nameOf(reference: String): String =
        body(reference).substringAfterLast('/')
}
