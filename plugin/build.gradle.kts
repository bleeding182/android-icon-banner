plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.3.1"
}

group = "com.github.bleeding182"
version = "0.1.0-SNAPSHOT"

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
            id = "com.github.bleeding182.iconbanner"
            implementationClass = "com.github.bleeding182.iconbanner.IconBannerPlugin"
            displayName = "Android Icon Banner"
            description = "Adds a per-variant banner to the Android launcher icon, so a dev build " +
                "is distinguishable from production on a device that has both installed."
            tags = listOf("android", "launcher-icon", "build-variants", "agp")
        }
    }
}

publishing {
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
