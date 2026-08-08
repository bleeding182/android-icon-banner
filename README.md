# Android Icon Banner

A Gradle plugin that stamps a corner ribbon onto your Android launcher icon, per build variant.

![Production icon, dev icon as a launcher shows it, and the full adaptive foreground](docs/preview.png)

Install a dev build and a production build on the same device and you cannot tell them apart in the
launcher, in recents, or in app info. Mark the variants you choose; the rest stay untouched.

```kotlin
plugins {
    id("com.android.application")
    id("com.github.bleeding182.iconbanner") version "0.0.1-SNAPSHOT"
}

android {
    buildTypes {
        debug { iconBanner { text = "DEV" } }   // release stays untouched
    }
}
```

No imports, no icon assets to maintain. A variant is bannered only if `text` resolves for it.

<img src="docs/preview-monochrome.gif" alt="A themed icon cycling through three system tints, the banner text staying a cutout" width="144" align="right">

Themed (monochrome) icons work too: the band is clipped out of the icon and the text punched through
it as a real cutout, so it stays legible under any tint the system picks.

Requires AGP 9 and Gradle 9, and a vector launcher icon. Applying it to a library or
dynamic-feature module does nothing, so a convention plugin can apply it everywhere.

<br clear="right">

## Setup

Until this lands on the Gradle Plugin Portal it publishes to GitHub Packages, which requires
authentication even for public packages. Put `gpr.user` and `gpr.key` (a token with `read:packages`)
in your `~/.gradle/gradle.properties`, then add the repository in `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        maven("https://maven.pkg.github.com/bleeding182/android-icon-banner") {
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                password = providers.gradleProperty("gpr.key").orNull
            }
        }
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
```

## Options

Style the ribbon once at the `android` level; flavors and build types only say what it reads.

```kotlin
android {
    iconBanner {
        color = "#FF0000"
        textColor = "#FFFFFF"
        corner = topLeft
        height = 20
        font = "Roboto Mono"
        weight = 700
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") { dimension = "environment"; iconBanner { text = "DEV" } }
        create("prod") { dimension = "environment" }
    }
}
```

| Property | Type | Default | Notes |
|---|---|---|---|
| `text` | `String`, `Provider<String>`, `null` | unset | Unset means no banner. `null` clears an inherited value. `""` gives an empty ribbon. Rendered verbatim. |
| `color` | `String` | `#FF0000` | Hex with optional alpha, or a `@color/…` / `?attr/…` reference. |
| `textColor` | `String` | `#FFFFFF` | Same forms. |
| `corner` | `BannerCorner` | `topLeft` | `topLeft`, `topRight`, `bottomLeft`, `bottomRight`. |
| `height` | `Int` | `20` | Width of the band, as a percentage of the icon's shorter edge. The only size knob; text auto-fits. |
| `font` | `String` | `Roboto Mono` | Any Google Fonts family, downloaded on first use and cached. |
| `weight` | `Int` | `700` | Must be a weight the family actually offers. |
| `italic` | `Boolean` | `false` | |

`corner` takes the bare names above in both Kotlin and Groovy, with no import.

### Precedence

Build type beats product flavor beats the project-level block — the same rule AGP uses for
everything else. A `null` only clears the value if nothing higher up assigns one.

```kotlin
android {
    iconBanner { text = "UNRELEASED" }               // every variant, unless overridden
    productFlavors {
        create("dev") { iconBanner { text = "DEV" } }
        create("prod") { iconBanner { text = null } } // no banner — unless a build type sets one
    }
    buildTypes {
        named("debug") { iconBanner { text = "DEBUG" } }   // wins over both, prodDebug included
    }
}
```

### Build metadata in the banner

`text` accepts a `Provider<String>`, which is only read when the icon is generated:

```kotlin
val gitSha = providers.exec { commandLine("git", "rev-parse", "--short", "HEAD") }
    .standardOutput.asText.map { it.trim() }

android {
    buildTypes {
        debug { iconBanner { text = gitSha.map { "DEV $it" } } }
    }
}
```

Assigning a provider enables the banner even before its value is known.

## Limitations

- **Vector icons only.** Legacy raster mipmaps are skipped, so a device below API 26 shows the
  unmodified icon. If a variant asks for a banner and there is no vector icon at all, the build
  fails rather than silently shipping an unmarked one.
- The bannered copy overrides the foreground **by resource name**, so anything else pointing at that
  drawable — a splash screen, say — gets the banner too.
- The build fails if the chosen font has no glyph for a character in your text.
- A variant with Android resources disabled gets no banner, with a warning.

## More

- [How it works](docs/how-it-works.md) — the resource override, geometry, themed icons, font caching.
- [Contributing](CONTRIBUTING.md) — building, the demo app, regenerating these previews.
- [Design notes](specs/icon-banner-gradle-plugin.md) — decisions and the reasoning behind them.

Modelled on a [browser-based banner generator](https://bleeding182.github.io/tools/banner_generator.html)
that produced the same ribbon as copy-pasteable XML.

## License

[MIT](LICENSE)
