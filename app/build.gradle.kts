import com.android.build.api.dsl.ApplicationProductFlavor

plugins {
    alias(libs.plugins.android.application)
    id("io.github.bleeding182.iconbanner")
}

// One flavor per kind of launcher icon; see "The demo app's flavors" in CONTRIBUTING.md.

// Five characters: a full SHA would be shrunk to a smear. Also the visual check that a Provider
// survives to the generator without being read during configuration.
val gitSha = providers.exec { commandLine("git", "rev-parse", "--short=5", "HEAD") }
    .standardOutput.asText.map { it.trim() }

fun ApplicationProductFlavor.demo(label: String, suffix: String) {
    dimension = "icon"
    // Every flavor, prod included: the whole point is having all five on one device at once, and a
    // flavor without a suffix is the one that replaces whatever else claims the bare applicationId.
    applicationIdSuffix = suffix
    // No app_name in main: five flavors that differ only in their icon are worth telling apart on the
    // launcher, and a label is the only text a launcher shows next to one.
    resValue("string", "app_name", label)
}

fun ApplicationProductFlavor.bannered() = iconBanner {
    text = "STAGING"
    banner("sha") { text = gitSha }
}

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

    // Everything in this block is a default for both banners, except `text`, which belongs to `main`
    // alone.
    iconBanner {
        color = "#FF0000"
        textColor = "#FFFFFF"
        corner = bottomRight
        // "STAGING" is seven characters, so the safe-zone chord sets the text size rather than
        // maxTextSize: 4.3dp on a launcher, just clear of the legibility warning. lineHeight is
        // non-default so the visual check notices if a setting stops reaching the generator.
        maxTextSize = 13
        lineHeight = 1.8
        font = "Black Ops One"
        weight = 400

        // Styled here, given its text by the bannered flavors alone — a banner nothing gives text to is
        // no banner, which is what leaves prod's icon untouched without saying so twice.
        //
        // Must stay opposite the main banner: adjacent corners cross near the middle of the icon and
        // would put an overlap in the README's preview. Its own monospaced face for the sha, and its own
        // monochromeAlpha because a themed icon has one colour for everything, so alpha is the only way
        // two banners tell themselves apart there.
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

    flavorDimensions += "icon"
    productFlavors {
        create("adaptiveVector") {
            demo("Vector", ".adaptivevector")
            bannered()
        }
        create("adaptiveRaster") {
            demo("Raster", ".adaptiveraster")
            bannered()
        }
        create("legacyRaster") {
            demo("Legacy", ".legacyraster")
            bannered()
        }
        create("plainVector") {
            demo("Plain", ".plainvector")
            bannered()
        }
        create("prod") {
            // No banner: the production icon is byte-for-byte the checked-in one.
            demo("Prod", ".prod")
        }
    }

    sourceSets {
        // prod is adaptiveVector without a banner, so it reads the same directory rather than keeping
        // a second copy of fourteen files for a difference to hide in.
        getByName("prod") { res.directories.add("src/adaptiveVector/res") }
    }

    buildFeatures {
        // Off by default in AGP 9. The five labels are the one thing each flavor needs beyond its
        // icons, and they belong next to the suffix they go with rather than in five values files.
        resValues = true
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
