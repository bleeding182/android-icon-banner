package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.api.BannerCorner
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import javax.inject.Inject

/**
 * One banner's appearance, shared by the `iconBanner { }` block ([IconBannerDsl]) and by each
 * named banner in it ([IconBannerSpec]).
 *
 * A property written in the block is a default for every banner in its scope; the same property on
 * a named banner overrides it. [text] is the exception — see [IconBannerDsl].
 */
abstract class IconBannerOptions @Inject constructor(objects: ObjectFactory) {

    /** Backing store for [text]. Only read once [textState] says it was assigned a value. */
    private val textProperty: Property<String> = objects.property(String::class.java)

    /**
     * Gradle cannot answer "was this set?" without forcing the value, which would run a user's
     * `providers.exec { git rev-parse }` during configuration.
     */
    internal var textState: TextState = TextState.NotSet
        private set

    /**
     * Banner text. A `String`, a `Provider<String>`, or `null`.
     *
     * Anything but `null` turns the banner on; `null` turns it off and stops an inherited value;
     * unassigned inherits. The getter hands a `Provider` back unevaluated.
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

    /**
     * Ribbon fill: a hex literal, `#RGB`, `#ARGB`, `#RRGGBB` or `#AARRGGBB`. The plugin has to paint the
     * value itself to raster an icon, so nothing it cannot parse is accepted — `?attr/...` least of all,
     * since a launcher inflates the icon without a theme and the build fails rather than ship one that
     * will not load.
     */
    abstract val color: Property<String>

    /** Text fill. Same accepted forms as [color]. */
    abstract val textColor: Property<String>

    /**
     * How opaque the band is in the themed (monochrome) icon, 0..100. Default 100.
     *
     * The only appearance a themed banner can choose: the system picks the colour, keeps the alpha,
     * and the text stays a cutout either way. Lower values read as a lighter shade of the same tint,
     * which is how two banners tell themselves apart there. [color] does not apply.
     */
    abstract val monochromeAlpha: Property<Int>

    /** Which corner the ribbon occupies. */
    abstract val corner: Property<BannerCorner>

    /**
     * How far out the ribbon sits: 0 the icon's centre, 100 where no text fits. Default 65, range
     * 20..95.
     *
     * How big the ribbon looks — the band spans the whole corner, not just its text. Fine-tuning
     * rather than layout: the text is fitted against `2r · √(1 - position²)`, so going out costs
     * size fast and coming in buys little. Text that stops fitting is drawn smaller, with a warning.
     */
    abstract val position: Property<Int>

    /**
     * Cap height as a percentage of the icon's shorter edge.
     *
     * An upper bound, not a size: text too long is drawn smaller and the band thins with it, so
     * raising this does nothing for text that is already length-bound.
     */
    abstract val maxTextSize: Property<Int>

    /**
     * Band thickness as a multiple of the cap height, measured across the band.
     *
     * Changes nothing about the text: at 1 the band is exactly the height of the glyphs, and the
     * surplus becomes clearance. It plays no part in the fit.
     */
    abstract val lineHeight: Property<Double>

    /**
     * Paint order where banners overlap: higher goes on top, ties in declaration order.
     *
     * Overlap is common — only *opposite* corners are guaranteed disjoint.
     */
    abstract val z: Property<Int>

    /** Google Fonts family name, e.g. `Roboto Mono`. */
    abstract val font: Property<String>

    /** Font weight on the `wght` axis, e.g. `400` or `700`. */
    abstract val weight: Property<Int>

    /** Whether to request the italic face. */
    abstract val italic: Property<Boolean>

    /** The four corners as members, so `corner = topLeft` needs no import. [BannerCorner] also works. */
    val topLeft: BannerCorner get() = BannerCorner.TOP_LEFT
    val topRight: BannerCorner get() = BannerCorner.TOP_RIGHT
    val bottomLeft: BannerCorner get() = BannerCorner.BOTTOM_LEFT
    val bottomRight: BannerCorner get() = BannerCorner.BOTTOM_RIGHT
}

/** Assignment state of [IconBannerOptions.text]. */
internal sealed interface TextState {
    /** Never mentioned: inherit from the next block in precedence order. */
    data object NotSet : TextState

    /** Assigned `null`: no banner, and no inheriting one either. */
    data object Cleared : TextState

    /** [provider] is read at execution time; [assigned] is the raw value, returned by the getter. */
    data class Assigned(val assigned: Any, val provider: Provider<String>) : TextState
}
