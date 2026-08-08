plugins {
    alias(libs.plugins.android.application)
    id("io.github.bleeding182.iconbanner")
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

    // Style once, here. Flavors only say what the ribbon reads.
    iconBanner {
        color = "#FF0000"
        textColor = "#FFFFFF"
        corner = bottomRight
        // "STAGING" is seven characters, so the chord across the icon's safe zone — not
        // maxTextSize — is what sets the text size: about 6.5 units of 108, or 4.3dp on a launcher,
        // just clear of the legibility warning. The line height is non-default on purpose, so the
        // visual check would notice the setting being dropped on the way to the generator: looser
        // than the default 1.5 wraps 11.7 units of band around that text, which reads better at this
        // size. It was 2.2 while the band was drawn 1/√2 too thin, where it drew 10.1; the same 2.2
        // now asks for 14.3, which is heavier than this preview wants. The number means what it says.
        maxTextSize = 13
        lineHeight = 1.8
        font = "Black Ops One"
        weight = 400
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            iconBanner {
                text = "STAGING"
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