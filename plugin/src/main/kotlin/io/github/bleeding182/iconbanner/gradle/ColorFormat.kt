package io.github.bleeding182.iconbanner.gradle

/**
 * Colour values are passed through to the generated `android:fillColor` untouched, so colour
 * resource references keep working. Only a literal that Android itself could not parse is rejected.
 *
 * Theme attributes are the one exception, and they are rejected rather than passed through: a
 * launcher inflates the icon from the app's resources with no theme attached, so `?attr/colorPrimary`
 * has nothing to resolve against. The result is a resolution failure and no icon at all — worse than
 * the build error, and impossible to diagnose from the launcher.
 */
internal object ColorFormat {

    private val HEX = Regex("#(?:[0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})")

    /** Returns [value] unchanged, or throws with a message naming [property] and [variantName]. */
    fun check(value: String, property: String, variantName: String): String {
        // `?colorPrimary` is as legal a theme reference as `?attr/colorPrimary`, and both are
        // useless here, so the whole prefix goes — with its own message, since "not a colour" would
        // be actively misleading for something Android does accept elsewhere.
        require(!value.startsWith("?")) {
            "iconBanner.$property for variant '$variantName' is '$value'. A launcher inflates the " +
                "launcher icon without a theme, so a theme attribute has nothing to resolve against " +
                "and the icon would fail to load. Use a colour resource (@color/name) or a hex " +
                "literal instead."
        }
        val valid = when {
            value.startsWith("#") -> HEX.matches(value)
            value.startsWith("@") -> typeOf(value).isNotEmpty() && nameOf(value).isNotEmpty()
            else -> false
        }
        require(valid) {
            "iconBanner.$property for variant '$variantName' is '$value', which is not a colour. " +
                "Use a hex literal (#RGB, #ARGB, #RRGGBB, #AARRGGBB) or a colour resource " +
                "(@color/name)."
        }
        return value
    }

    private fun body(reference: String): String = reference.drop(1).removePrefix("+").substringAfter(':')

    private fun typeOf(reference: String): String =
        body(reference).substringBefore('/', missingDelimiterValue = "")

    private fun nameOf(reference: String): String =
        body(reference).substringAfterLast('/')
}
