plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.3.1"
}

group = "io.github.bleeding182"
// A release build passes -PpluginVersion=<tag without the leading v>; see .github/workflows/publish.yml.
version = providers.gradleProperty("pluginVersion").getOrElse("0.0.1-SNAPSHOT")

// AGP 9 supports JDK 17, so the plugin must load on a 17 daemon. Without a toolchain the bytecode
// level is whatever JVM ran the build, and a consumer on 17 gets an UnsupportedClassVersionError.
// CI asserts class-file major 61 and resolves from mavenLocal on a 17 daemon.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    // AGP variant API. compileOnly: the consuming build always brings its own AGP.
    compileOnly("com.android.tools.build:gradle-api:9.3.1")

    // The WebP reader, as WEBP_READER_COORDINATES pins it. Test-only on purpose: a consuming build
    // resolves it from its own project, so this must never reach the plugin's runtime classpath.
    // It puts a genuine webp within reach of the pure generator's tests, and does not leak into the
    // TestKit builds — pluginUnderTestMetadata is built from the main runtime classpath, so those
    // still exercise the real lazy resolution.
    testImplementation("com.twelvemonkeys.imageio:imageio-webp:3.14.0")

    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(gradleTestKit())
}

gradlePlugin {
    website = "https://github.com/bleeding182/android-icon-banner"
    vcsUrl = "https://github.com/bleeding182/android-icon-banner"
    plugins {
        create("iconBanner") {
            id = "io.github.bleeding182.iconbanner"
            implementationClass = "io.github.bleeding182.iconbanner.IconBannerPlugin"
            displayName = "Icon Banner for Android"
            description = "Adds a per-variant banner to the Android launcher icon, so a dev build " +
                "is distinguishable from production on a device that has both installed."
            tags = listOf("android", "launcher-icon", "build-variants", "agp")
        }
    }
}

// Two builds of the same source must produce the same jar. `includeEmptyDirs` is the one that bites:
// a warm cache restores empty directories left by a package rename and the Jar packages them. The
// other two already hold on Gradle 9.5, and are pinned so an upgrade cannot flip them back.
tasks.withType<Jar>().configureEach {
    includeEmptyDirs = false
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

publishing {
    publications {
        // `pluginMaven` is the plugin itself; the `...PluginMarkerMaven` siblings are stubs that
        // only redirect an id to it. Both are added late, hence the lazy match over `named(...)`.
        withType<MavenPublication>().configureEach {
            if (name != "pluginMaven") return@configureEach
            pom {
                name = "Android Icon Banner"
                description = "A Gradle plugin that adds a per-variant banner to an Android app's " +
                    "launcher icon, so a dev build is distinguishable from production on a device " +
                    "that has both installed."
                url = "https://github.com/bleeding182/android-icon-banner"
                licenses {
                    license {
                        name = "MIT License"
                        url = "https://github.com/bleeding182/android-icon-banner/blob/main/LICENSE"
                        distribution = "repo"
                    }
                }
                developers {
                    developer {
                        id = "bleeding182"
                        name = "David Medenjak"
                        url = "https://github.com/bleeding182"
                    }
                }
                scm {
                    url = "https://github.com/bleeding182/android-icon-banner"
                    connection = "scm:git:https://github.com/bleeding182/android-icon-banner.git"
                    developerConnection = "scm:git:ssh://git@github.com/bleeding182/android-icon-banner.git"
                }
            }
        }
    }

    repositories {
        // A second copy of every release and somewhere for snapshots to go — the Portal takes
        // releases only. Reading from here needs a token even though the package is public.
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/bleeding182/android-icon-banner")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR")).orNull
                password = providers.gradleProperty("gpr.key")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
