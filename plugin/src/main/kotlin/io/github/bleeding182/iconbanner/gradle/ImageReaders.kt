package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.api.ImageCodecs
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

/**
 * The WebP reader, pinned.
 *
 * Android Studio writes the legacy launcher mipmaps as WebP and the JDK ships no reader for it — on 17
 * and 21 `ImageIO` offers JPEG, PNG, BMP, GIF, TIFF and WBMP and nothing else. This one is pure Java,
 * reader-only, 580 KB with no third-party transitives, BSD-3-Clause.
 *
 * Kept out of the plugin's own `implementation` dependencies: that would land it on every consuming
 * buildscript's classpath. See [imageReaderConfiguration].
 */
internal const val WEBP_READER_COORDINATES = "com.twelvemonkeys.imageio:imageio-webp:3.14.0"

/** The configuration [WEBP_READER_COORDINATES] is resolved from, in the consuming project. */
internal const val IMAGE_READER_CONFIGURATION = "iconBannerImageReaders"

/**
 * Where the bitmap readers come from: one configuration in the *consuming* project, so the reader stays
 * off every buildscript's classpath and a project whose bitmaps the JDK reads never fetches one.
 *
 * `defaultDependencies` rather than a plain declaration, so a declared version wins over the pinned
 * one. Resolvable but not consumable: nothing outside this build should see it.
 */
internal fun imageReaderConfiguration(project: Project): Configuration =
    project.configurations.findByName(IMAGE_READER_CONFIGURATION)
        ?: project.configurations.create(IMAGE_READER_CONFIGURATION) {
            description = "Image readers for bannering the launcher icon's bitmap files."
            isCanBeResolved = true
            isCanBeConsumed = false
            defaultDependencies { add(project.dependencies.create(WEBP_READER_COORDINATES)) }
        }

/**
 * The reader jars, through a **lenient** artifact view, which is load-bearing rather than defensive.
 *
 * The configuration cache resolves a task's file collections when it stores its entry, which is why the
 * `@Internal` classpath is only half the laziness this design wanted. Strict, that would fail a build
 * whose icons are all vectors over a repository it never needed. Lenient, an unresolvable reader is an
 * empty classpath, which [ClasspathImageCodecs] reports only once an undecodable bitmap turns up.
 */
internal fun imageReaderFiles(configuration: Configuration): FileCollection =
    configuration.incoming.artifactView { isLenient = true }.files

/**
 * What [configuration] will be resolved from, as coordinates, for the task to fingerprint.
 *
 * Read rather than hard-coded, so a build script that pins its own version refingerprints the task
 * too. The `ifEmpty` mirrors `defaultDependencies`, which applies exactly when nothing was declared:
 * reading the default out of the configuration is not an option, since the action that adds it does not
 * run until the configuration is resolved, and resolving is the thing to avoid.
 *
 * Lazy, because a build script may still be declaring a version when the task is wired.
 */
internal fun declaredImageReaderCoordinates(
    project: Project,
    configuration: Configuration,
): Provider<List<String>> =
    project.provider {
        configuration.dependencies
            .map { "${it.group}:${it.name}:${it.version}" }
            .ifEmpty { listOf(WEBP_READER_COORDINATES) }
            .sorted()
    }

/**
 * Registers the reader classpath, on the first bitmap the JDK could not decode and never at all for an
 * icon graph of pure vectors or of PNGs.
 *
 * @param classpath called rather than passed, so that without the configuration cache nothing is
 * resolved until [ensureReadersAvailable] — see [imageReaderFiles] for what the cache does instead.
 */
internal class ClasspathImageCodecs(
    private val variant: String,
    private val coordinates: List<String>,
    private val classpath: () -> Set<File>,
) : ImageCodecs {

    override fun ensureReadersAvailable(resourcePath: String) {
        val jars = classpath()
        // Empty because the view is lenient: this is where an unresolvable reader is finally reported,
        // and only for a file the JDK already failed on. Names both halves, since either could be the
        // real fault.
        if (jars.isEmpty()) {
            throw GradleException(
                "icon banner ($variant): no image reader available to this build could decode " +
                    "$resourcePath, and the additional readers for it could not be resolved: " +
                    "${coordinates.joinToString(", ")}. The plugin resolves those from the " +
                    "'$IMAGE_READER_CONFIGURATION' configuration of this project, so add a " +
                    "repository that serves them — mavenCentral() is enough — or run one online " +
                    "build to warm the dependency cache if this build is offline. `gradle " +
                    "dependencies --configuration $IMAGE_READER_CONFIGURATION` reports why the " +
                    "resolution came back empty. If it reports nothing wrong, suspect the file " +
                    "itself: a truncated or corrupt image fails here just the same."
            )
        }
        ImageReaders.install(jars)
    }
}

/**
 * Makes image readers that are not on the plugin's own classpath visible to `ImageIO`.
 *
 * The registry is built lazily, once, and cached per thread group, which is what makes the order here
 * matter. Established by experiment: setting the context class loader *before* the first touch of
 * `ImageIO` works, setting it afterwards does not, and a `scanForPlugins()` after the fact works
 * either way. Inside a Gradle daemon there is no telling what touched `ImageIO` first, so the scan is
 * unconditional.
 *
 * A `WorkerExecutor` with `classLoaderIsolation` was considered and rejected: the decode sits deep
 * inside the pure generator's call graph, so a worker would mean serialising the whole request across a
 * boundary, and it would move the task's logging and failure reporting out of the task.
 */
internal object ImageReaders {

    /**
     * One loader per jar set for the daemon's lifetime, and deliberately so: the registry keeps the
     * SPI instances it is handed, so a fresh loader every build would register another reader every
     * build and pin every loader it ever built.
     */
    private val loaders = ConcurrentHashMap<Set<File>, ClassLoader>()

    /**
     * Synchronized for the registry rather than for [loaders]: two variants' generate tasks can run in
     * parallel in one daemon, and `IIORegistry` is not built for two threads registering into it at once.
     *
     * @return the loader the readers came from, which is also what makes the registration observable:
     * a test can tell a reader of ours apart from one that was on the classpath all along.
     */
    @Synchronized
    fun install(jars: Collection<File>): ClassLoader {
        val loader = loaders.computeIfAbsent(jars.toSet()) {
            // Parent is the platform loader, not the plugin's own: these readers need java.desktop and
            // nothing else, so a copy on some buildscript's classpath is not delegated to. It does not
            // stop one already registered from answering a decode — IIORegistry keys providers by Class,
            // so a stray copy registers alongside ours rather than losing to it.
            URLClassLoader(
                jars.map { it.toURI().toURL() }.toTypedArray(),
                ClassLoader.getPlatformClassLoader(),
            )
        }
        // Scanned on every call rather than once beside the loader: the registry is per thread group,
        // so the next build in this daemon may well face an empty one.
        val previous = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = loader
        try {
            ImageIO.scanForPlugins()
        } finally {
            Thread.currentThread().contextClassLoader = previous
        }
        return loader
    }
}
