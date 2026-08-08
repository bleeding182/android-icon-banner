plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.3.1"
}

group = "io.github.bleeding182"
// A release build passes -PpluginVersion=<tag without the leading v>; see .github/workflows/publish.yml.
version = providers.gradleProperty("pluginVersion").getOrElse("0.0.1-SNAPSHOT")

// AGP 9 supports JDK 17, so the plugin has to be loadable on a 17 daemon: class-file major 61 and
// `org.gradle.jvm.version = 17` in the module metadata. Without a toolchain the bytecode level is
// whatever JVM happens to run the build — 25 via the root build's gradle-daemon-jvm.properties, or
// whatever JAVA_HOME points at for a plain `-p plugin` invocation — and a consumer on 17 then gets
// "requires at least JVM runtime version 21" or an UnsupportedClassVersionError. CI asserts the 61,
// and resolves the plugin from mavenLocal on a 17 daemon, in .github/workflows/ci.yml.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    // AGP variant API. compileOnly: the consuming build always brings its own AGP.
    compileOnly("com.android.tools.build:gradle-api:9.3.1")

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
            displayName = "Android Icon Banner"
            description = "Adds a per-variant banner to the Android launcher icon, so a dev build " +
                "is distinguishable from production on a device that has both installed."
            tags = listOf("android", "launcher-icon", "build-variants", "agp")
        }
    }
}

// Two builds of the same source have to produce the same jar. Without `includeEmptyDirs = false` a
// warm build cache that predates the com.github -> io.github rename restores the now-empty
// com/github/** directories into build/classes, the Jar task packages them, and the sha256 differs
// from a build on a fresh machine. The other two already hold on Gradle 9.5 — entries come out dated
// 1980-02-01 — and are pinned so a Gradle upgrade cannot flip them back.
tasks.withType<Jar>().configureEach {
    includeEmptyDirs = false
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

publishing {
    publications {
        // `pluginMaven` is the publication `java-gradle-plugin` creates for the plugin itself; the
        // `...PluginMarkerMaven` siblings are generated stubs that only redirect an id to it, and
        // need no metadata. Both are added late, hence the lazy match rather than `named(...)`.
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
        // Testing ground until the plugin settles; the Gradle Plugin Portal is the eventual home
        // and `plugin-publish` above already targets it. GitHub Packages needs a token even for
        // public packages, so consumers have to supply credentials too.
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
