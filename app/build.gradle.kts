plugins {
    alias(libs.plugins.android.application)
    id("io.github.bleeding182.iconbanner")
}

// Five characters: a full SHA would be shrunk to a smear. Also the visual check that a Provider
// survives to the generator without being read during configuration.
val gitSha = providers.exec { commandLine("git", "rev-parse", "--short=5", "HEAD") }
    .standardOutput.asText.map { it.trim() }

android {
    namespace = "io.github.bleeding182.android.iconbanner"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.bleeding182.android.iconbanner"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Style once here; flavors only say what the ribbons read. Everything in this block is a default
    // for both banners, except `text`, which belongs to `main` alone.
    iconBanner {
        color = "#FF0000"
        textColor = "#FFFFFF"
        corner = bottomRight
        // "STAGING" is seven characters, so the safe-zone chord sets the text size rather than
        // maxTextSize: 4.3dp on a launcher, just clear of the legibility warning. lineHeight is
        // non-default on purpose, so the visual check notices if a setting stops reaching the
        // generator.
        maxTextSize = 13
        lineHeight = 1.8
        font = "Black Ops One"
        weight = 400

        // Styled here, given its text by the staging flavor alone — a banner nothing gives text to
        // is no banner, which is what leaves prod's icon untouched without saying so twice.
        //
        // Must stay opposite `main`: adjacent corners cross near the middle of the icon and would
        // put an overlap in the README's preview. Pushed out to 85 so it reads as a tight tab,
        // which five characters have the room to pay for and seven would not.
        //
        // Its own face, overriding the block's: a hex sha reads better monospaced, and the font
        // task fetches both and hands each banner the one it asked for.
        //
        // The themed icon has one colour for everything, so the two banners only tell themselves
        // apart there by alpha — which is what monochromeAlpha is for and what the preview shows.
        banner("sha") {
            corner = topLeft
            position = 85
            maxTextSize = 6
            color = "#1A1A1A"
            monochromeAlpha = 60
            font = "Roboto Mono"
            weight = 700
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            iconBanner {
                text = "STAGING"
                banner("sha") {
                    text = gitSha
                }
            }
        }
        create("prod") {
            dimension = "environment"
            // No banner: the production icon is byte-for-byte the checked-in one.
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}