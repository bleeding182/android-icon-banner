package com.github.bleeding182.iconbanner.gradle

import com.github.bleeding182.iconbanner.api.BannerCorner
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import javax.inject.Inject

/**
 * The `iconBanner { }` block. Registered by AGP into three slots: the `android { }` extension
 * (project-level defaults), every build type, and every product flavor.
 *
 * Everything except [text] is an ordinary lazy [Property] merged by `orElse` chaining, so nothing
 * is ever evaluated during configuration.
 */
abstract class IconBannerDsl @Inject constructor(objects: ObjectFactory) {

    /** Backing store for [text]. Only read once [textState] says it was assigned a value. */
    private val textProperty: Property<String> = objects.property(String::class.java)

    /**
     * Whether and how [text] was assigned. Gradle cannot answer "was this property set?" without
     * forcing its value, and forcing would run a user's `providers.exec { git rev-parse }` during
     * configuration. Tracking the assignment separately gives the intended semantics without ever
     * reading the value.
     */
    internal var textState: TextState = TextState.NotSet
        private set

    /**
     * Banner text. Accepts a `String`, a `Provider<String>`, or `null`.
     *
     * Assigning anything other than `null` turns the banner on for the variants this block applies
     * to; assigning `null` turns it off and stops a value inherited from a lower-precedence block.
     * Leaving it unassigned inherits.
     *
     * The getter returns the value exactly as it was assigned — a `Provider` is handed back
     * unevaluated.
     */
    var text: Any?
        get() = when (val state = textState) {
            TextState.NotSet, TextState.Cleared -> null
            is TextState.Assigned -> state.assigned
        }
        set(value) {
            textState = when (value) {
                null -> TextState.Cleared
                is String -> {
                    textProperty.set(value)
                    TextState.Assigned(value, textProperty)
                }

                is Provider<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    textProperty.set(value as Provider<String>)
                    TextState.Assigned(value, textProperty)
                }

                else -> throw IllegalArgumentException(
                    "iconBanner.text must be a String, a Provider<String> or null, " +
                        "but was a ${value.javaClass.name}."
                )
            }
        }

    /** Ribbon fill. A hex literal, or a `@color/...` / `?attr/...` reference passed straight through. */
    abstract val color: Property<String>

    /** Text fill. Same accepted forms as [color]. */
    abstract val textColor: Property<String>

    /** Which corner the ribbon occupies. */
    abstract val corner: Property<BannerCorner>

    /** Ribbon band width as a percentage of the icon's edge length. */
    abstract val height: Property<Int>

    /** Google Fonts family name, e.g. `Roboto Mono`. */
    abstract val font: Property<String>

    /** Font weight on the `wght` axis, e.g. `400` or `700`. */
    abstract val weight: Property<Int>

    /** Whether to request the italic face. */
    abstract val italic: Property<Boolean>

    /**
     * The four corners, as members, so `corner = topLeft` needs no import in a build script.
     *
     * [BannerCorner] is public and works just as well if you would rather name it explicitly.
     */
    val topLeft: BannerCorner get() = BannerCorner.TOP_LEFT
    val topRight: BannerCorner get() = BannerCorner.TOP_RIGHT
    val bottomLeft: BannerCorner get() = BannerCorner.BOTTOM_LEFT
    val bottomRight: BannerCorner get() = BannerCorner.BOTTOM_RIGHT
}

/** Assignment state of [IconBannerDsl.text]. */
internal sealed interface TextState {
    /** Never mentioned: inherit from the next block in precedence order. */
    data object NotSet : TextState

    /** Assigned `null`: no banner, and no inheriting one either. */
    data object Cleared : TextState

    /**
     * Assigned a `String` or a `Provider<String>`. [provider] is only read at execution time.
     * [assigned] is the raw value the user wrote, returned by the getter.
     */
    data class Assigned(val assigned: Any, val provider: Provider<String>) : TextState
}
