package io.github.bleeding182.iconbanner.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResourceRefTest {

    @Test
    fun `parses a reference to the app's own resource`() {
        assertEquals(ResourceRef("mipmap", "ic_launcher"), ResourceRef.parse("@mipmap/ic_launcher"))
    }

    /** Dropping the package would turn the framework's icon into a same-named one of the app's. */
    @Test
    fun `a package-qualified reference is not parsed`() {
        assertNull(ResourceRef.parse("@android:mipmap/sym_def_app_icon"))
        assertNull(ResourceRef.parse("@*android:drawable/x"))
        assertNull(ResourceRef.parse("@com.example:drawable/x"))
    }

    @Test
    fun `a theme attribute is not parsed`() {
        assertNull(ResourceRef.parse("?attr/appIcon"))
        assertNull(ResourceRef.parse("?android:attr/appIcon"))
    }

    @Test
    fun `a reference without its at sign is not parsed`() {
        assertNull(ResourceRef.parse("mipmap/ic_launcher"))
    }

    /** aapt2 trims attribute values, so a padded reference builds and has to resolve here too. */
    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals(ResourceRef("drawable", "x"), ResourceRef.parse(" @drawable/x "))
        assertEquals(ResourceRef("mipmap", "ic_launcher"), ResourceRef.parse("\n    @mipmap/ic_launcher\n"))
        assertTrue(ResourceRef.isFramework(" @android:mipmap/sym_def_app_icon"))
    }

    @Test
    fun `a reference into another package is told apart from one that is no reference at all`() {
        assertEquals(ResourceRef("mipmap", "ic_launcher"), ResourceRef.withoutPackage("@com.example.app:mipmap/ic_launcher"))
        assertEquals(ResourceRef("drawable", "x"), ResourceRef.withoutPackage(" @*com.example:drawable/x "))
        // The framework has its own failure, and an unqualified reference is not this one.
        assertNull(ResourceRef.withoutPackage("@android:mipmap/sym_def_app_icon"))
        assertNull(ResourceRef.withoutPackage("@mipmap/ic_launcher"))
        assertNull(ResourceRef.withoutPackage("?android:attr/appIcon"))
    }

    @Test
    fun `a theme attribute is recognised with or without a package`() {
        assertTrue(ResourceRef.isThemeAttribute("?attr/appIcon"))
        assertTrue(ResourceRef.isThemeAttribute(" ?android:attr/appIcon"))
        assertFalse(ResourceRef.isThemeAttribute("@mipmap/ic_launcher"))
    }
}
