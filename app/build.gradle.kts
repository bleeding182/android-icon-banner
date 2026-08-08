import com.github.bleeding182.iconbanner.api.BannerCorner
import com.github.bleeding182.iconbanner.gradle.iconBanner

plugins {
    alias(libs.plugins.android.application)
    id("com.github.bleeding182.iconbanner")
}

android {
    namespace = "com.github.bleeding182.android.iconbanner"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.github.bleeding182.android.iconbanner"
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
        corner = BannerCorner.BOTTOM_RIGHT
        height = 32
        font = "Black Ops One"
        weight = 400
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            iconBanner {
                text = "2026"
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