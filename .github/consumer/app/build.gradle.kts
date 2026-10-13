plugins {
    alias(libs.plugins.android.application)
    // The README's line, as a user pastes it: by id and version, in the module rather than the root.
    id("io.github.bleeding182.iconbanner") version "@PLUGIN_VERSION@"
}

android {
    namespace = "com.example.consumer"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.consumer"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    iconBanner {
        color = "#FF00FF" // a colour nothing in the template icon uses, so check.sh can look for it
    }

    flavorDimensions += "environment"
    productFlavors {
        create("staging") { dimension = "environment"; iconBanner { text = "STAGING" } }
        create("prod") { dimension = "environment" }   // production stays untouched
    }
}
