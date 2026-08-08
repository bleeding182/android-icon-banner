package com.github.bleeding182.iconbanner.gradle

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

    /** Copies one of the checked-in source trees under `src/test/resources/testkit` into the fixture. */
    fun overlay(name: String) = apply {
        val root = requireNotNull(javaClass.classLoader.getResource("testkit/$name")) {
            "No testkit fixture named '$name'"
        }
        File(root.toURI()).copyRecursively(dir, overwrite = true)
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
                    // AGP and the plugin under test have to share one class loader scope, or the
                    // plugin cannot see the AGP types it is written against.
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
            import com.github.bleeding182.iconbanner.api.BannerCorner
            import com.github.bleeding182.iconbanner.gradle.iconBanner

            plugins {
                id("com.android.application")
                id("com.github.bleeding182.iconbanner")
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
        .withArguments(*arguments)

    /** The generated resource directory AGP wires the task's output into. */
    fun generatedResources(variant: String): File =
        File(dir, "build/generated/res/generate${variant.replaceFirstChar { it.uppercaseChar() }}IconBanner")

    fun generatedForeground(variant: String): File =
        File(generatedResources(variant), "drawable/ic_launcher_foreground.xml")

    companion object {
        // Must match the AGP the plugin is compiled against; there is no version catalog here.
        private const val AGP_VERSION = "9.3.1"
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
