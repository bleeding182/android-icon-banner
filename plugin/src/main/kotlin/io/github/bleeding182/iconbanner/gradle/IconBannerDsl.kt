package io.github.bleeding182.iconbanner.gradle

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

/** The banner made of a block's own properties. Reserved: [IconBannerDsl.banner] refuses the name. */
internal const val MAIN_BANNER: String = "main"

/**
 * The `iconBanner { }` block, which AGP registers into the `android { }` extension, every build
 * type and every product flavor.
 *
 * Its own properties do double duty: defaults for every banner in this scope, *and* the banner
 * named [MAIN_BANNER]. [IconBannerOptions.text] is the only property that does not fall through
 * to named banners — inheriting it would draw the same marker twice. A named banner never given
 * text is silently no banner, so a style can be declared at project level and its text on one
 * flavor.
 */
abstract class IconBannerDsl @Inject constructor(objects: ObjectFactory) : IconBannerOptions(objects) {

    /** Public so a Groovy script gets `banners { sha { … } }` free. Kotlin should use [banner]. */
    val banners: NamedDomainObjectContainer<IconBannerSpec> =
        objects.domainObjectContainer(IconBannerSpec::class.java)

    /**
     * A [NamedDomainObjectContainer] iterates alphabetically, and declaration order is what breaks
     * ties in [IconBannerOptions.z].
     */
    private val declared = mutableListOf<String>()

    internal val bannerNames: List<String> get() = declared

    init {
        // The SAM constructor is required: whenObjectAdded is overloaded on Action and Groovy's
        // Closure, and a bare lambda cannot pick between them. The element arrives as the receiver.
        banners.whenObjectAdded(Action<IconBannerSpec> {
            require(getName() != MAIN_BANNER) {
                "iconBanner: '$MAIN_BANNER' is reserved for the block's own properties. Write text, " +
                    "color and the rest directly in iconBanner { } rather than in " +
                    "banner(\"$MAIN_BANNER\") { }."
            }
            declared += getName()
        })
    }

    /**
     * Declares a banner, or reaches the one this block already declared. `maybeCreate`, because a
     * flavor writing `banner("sha") { color = … }` is refining, not colliding.
     */
    fun banner(name: String): IconBannerSpec = banners.maybeCreate(name)

    /** [Action] rather than a Kotlin function type, so a Groovy closure binds to it too. */
    fun banner(name: String, configure: Action<IconBannerSpec>): IconBannerSpec =
        banner(name).also(configure::execute)
}
