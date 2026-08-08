package io.github.bleeding182.iconbanner.gradle

import org.gradle.api.Named
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

/**
 * One named banner, declared with [IconBannerDsl.banner].
 *
 * Separate from [IconBannerDsl] only because a container element takes its name as the first
 * constructor argument, which AGP's own instantiation cannot supply.
 */
abstract class IconBannerSpec @Inject constructor(
    private val bannerName: String,
    objects: ObjectFactory,
) : IconBannerOptions(objects), Named {

    override fun getName(): String = bannerName

    /**
     * Drops this banner for the enclosing block's scope. Sugar for `text = null` and exactly that:
     * each DSL slot owns its own container, so nothing can be literally removed from another's.
     */
    fun remove() {
        text = null
    }
}
