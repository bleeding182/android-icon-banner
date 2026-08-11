package io.github.bleeding182.iconbanner.gradle

import io.github.bleeding182.iconbanner.api.FontSpec
import io.github.bleeding182.iconbanner.font.FontCache
import io.github.bleeding182.iconbanner.generator.testFont
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File

/**
 * A throwaway single-module Android app for the TestKit suite.
 *
 * Every case here costs a real Gradle build, so the fixture stays as small as an Android
 * application module can be: one manifest, one adaptive icon, one foreground vector.
 */
internal class IconBannerFixture(val dir: File) {

    /**
     * A private font cache, pre-loaded with the checked-in face, so builds under test never reach
     * the network and never write into the developer's real shared cache.
     */
    private val fontCache: File = File(dir, ".font-cache")

    init {
        primeFont(FontSpec(BannerDefaults.FONT, BannerDefaults.WEIGHT, BannerDefaults.ITALIC))
    }

    /**
     * Records [spec] in the private cache as already downloaded, serving the checked-in face.
     *
     * A build asking for a second font would otherwise reach the network, and the bytes are beside
     * the point for every assertion here — what matters is which faces the font task was asked for
     * and what it names the files it writes.
     */
    fun primeFont(spec: FontSpec) = apply {
        val cache = FontCache(fontCache.toPath())
        // A genuine gstatic URL: the provider refuses a cached URL from any other origin.
        val url = "$FIXTURE_FONT_URL?${FontCache.specKey(spec)}"
        cache.recordResolvedUrl(spec, url)
        testFont.readBytes().inputStream().use { cache.store(url, it) }
    }

    /** Copies one of the checked-in source trees under `src/test/resources/testkit` into the fixture. */
    fun overlay(name: String) = apply {
        val root = requireNotNull(javaClass.classLoader.getResource("testkit/$name")) {
            "No testkit fixture named '$name'"
        }
        File(root.toURI()).copyRecursively(dir, overwrite = true)
    }

    /**
     * Adds a legacy raster launcher icon to `main`, beside the base overlay's adaptive one — the shape
     * every Android Studio project has.
     *
     * Written rather than checked in as another overlay: both raster cases want the *same* picture in
     * two formats, and one copy of it in the repository is enough.
     */
    fun legacyIcon(qualifiers: String, fileName: String, bytes: ByteArray) = apply {
        File(dir, "src/main/res/$qualifiers/$fileName")
            .apply { parentFile.mkdirs() }
            .writeBytes(bytes)
    }

    /** Writes the build script. [androidExtras] is spliced into the `android { }` block. */
    fun buildScript(androidExtras: String) = apply {
        File(dir, "settings.gradle.kts").writeText(
            """
            buildscript {
                repositories {
                    google()
                    mavenCentral()
                }
                dependencies {
                    // AGP and the plugin must share one class loader scope.
                    classpath("com.android.tools.build:gradle:$AGP_VERSION")
                    classpath(files(${pluginClasspath()}))
                }
            }
            dependencyResolutionManagement {
                repositories {
                    google()
                    mavenCentral()
                }
            }
            rootProject.name = "fixture"
            """.trimIndent()
        )
        File(dir, "gradle.properties").writeText(
            """
            org.gradle.jvmargs=-Xmx2048m
            org.gradle.configuration-cache=true
            android.useAndroidX=true
            """.trimIndent()
        )
        File(dir, "local.properties").writeText("sdk.dir=${androidSdk().invariantPath()}\n")
        File(dir, "build.gradle.kts").writeText(
            """
            // Deliberately no imports: build scripts must not need any, and a flavor-scoped block binding
            // to the wrong receiver would still compile.
            plugins {
                id("com.android.application")
                id("io.github.bleeding182.iconbanner")
            }

            android {
                namespace = "com.example.fixture"
                compileSdk {
                    version = release($COMPILE_SDK)
                }
                defaultConfig {
                    minSdk = 24
                }
            $androidExtras
            }
            """.trimIndent()
        )
    }

    fun runner(vararg arguments: String): GradleRunner = GradleRunner.create()
        .withProjectDir(dir)
        .withTestKitDir(gradleUserHome())
        .withArguments(*arguments, "-P$FONT_CACHE_PROPERTY=${fontCache.invariantPath()}")

    /** The font the builds under test resolve to, for assertions that need to render text. */
    fun fontFile(): File = testFont

    /** The generated resource directory AGP wires the task's output into. */
    fun generatedResources(variant: String): File =
        File(dir, "build/generated/res/generate${variant.replaceFirstChar { it.uppercaseChar() }}IconBanner")

    fun generatedForeground(variant: String): File =
        File(generatedResources(variant), "drawable/ic_launcher_foreground.xml")

    /** Where the font task drops the variant's faces, one file per distinct one. */
    fun fontDirectory(variant: String): File = File(dir, "build/intermediates/icon_banner/font/$variant")

    companion object {
        // Must match the AGP the plugin is compiled against; there is no version catalog here.
        private const val AGP_VERSION = "9.3.1"

        /**
         * The genuine gstatic URL for the checked-in face, so the primed cache holds exactly what a
         * real download would have produced rather than a synthetic entry.
         */
        private const val FIXTURE_FONT_URL =
            "https://fonts.gstatic.com/s/robotomono/v31/" +
                "L0xuDF4xlVMF-BfR8bXMIhJHg45mwgGEFl0_Of2PQw.ttf"

        private const val COMPILE_SDK = 37

        /**
         * The plugin under test goes on the fixture's own `buildscript` classpath rather than
         * through `GradleRunner.withPluginClasspath()`. TestKit injects that classpath into a
         * parent class loader, which cannot see the AGP classes the `plugins { }` block resolves
         * into the build script's own scope — the plugin then dies on
         * `NoClassDefFoundError: ApplicationAndroidComponentsExtension`. A `buildscript` block puts
         * both in one scope.
         */
        private fun pluginClasspath(): String {
            val metadata = checkNotNull(
                IconBannerFixture::class.java.classLoader
                    .getResourceAsStream("plugin-under-test-metadata.properties")
            ) { "Run the tests through Gradle so plugin-under-test-metadata.properties exists." }
            val properties = java.util.Properties().apply { metadata.use(::load) }
            return properties.getProperty("implementation-classpath")
                .split(File.pathSeparator)
                .joinToString(", ") { "\"${File(it).invariantPath()}\"" }
        }

        /**
         * Reusing the real Gradle user home keeps the suite from re-downloading AGP into a private
         * TestKit cache on every fresh machine.
         */
        private fun gradleUserHome(): File =
            System.getenv("GRADLE_USER_HOME")?.let(::File)
                ?: File(System.getProperty("user.home"), ".gradle")

        private fun androidSdk(): File {
            val candidates = listOfNotNull(
                System.getenv("ANDROID_HOME"),
                System.getenv("ANDROID_SDK_ROOT"),
                File(System.getProperty("user.home"), "Android/Sdk").path,
            )
            val sdk = candidates.map(::File).firstOrNull { it.isDirectory }
            assumeTrue(sdk != null, "No Android SDK; set ANDROID_HOME to run the TestKit suite.")
            return sdk!!
        }

        private fun File.invariantPath(): String = path.replace('\\', '/')
    }
}
