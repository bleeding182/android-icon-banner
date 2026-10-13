pluginManagement {
    repositories {
        // The one line a real project does not have: the plugin under test, from the repository
        // check.sh was given. Exclusive, so it cannot quietly resolve from the Portal instead.
        exclusiveContent {
            forRepository { maven { url = uri("@MAVEN_REPO@") } }
            filter {
                includeGroup("io.github.bleeding182")
                includeGroup("io.github.bleeding182.iconbanner")
            }
        }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Consumer"
include(":app")
