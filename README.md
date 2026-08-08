# Android Icon Banner

A Gradle plugin that stamps a corner ribbon onto your Android launcher icon, per build variant.

Install a dev build and a production build on the same device and you cannot tell them apart in the
launcher, in recents, or in app info. This marks the ones you choose and leaves the rest byte-for-byte
untouched.

![Production icon, dev icon as a launcher shows it, and the full adaptive foreground](docs/preview.png)

*Left: production, unmodified. Middle: the dev variant as a launcher masks it. Right: the full 108dp
adaptive foreground that was generated.*

## Install

While the plugin is still being tested it publishes to GitHub Packages. In `settings.gradle.kts`:

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

GitHub Packages requires authentication even for public packages, so `gpr.user` and `gpr.key` (a
personal access token with `read:packages`) need to be in your `~/.gradle/gradle.properties`.

Then, in your application module:

```kotlin
plugins {
    id("com.android.application")
    id("com.github.bleeding182.iconbanner")
}
```

## Use

```kotlin
android {
    // Style once, for every variant.
    iconBanner {
        color = "#FF0000"
        textColor = "#FFFFFF"
        corner = topLeft
        height = 20
        font = "Roboto Mono"
        weight = 700
    }

    productFlavors {
        create("dev") { iconBanner { text = "DEV" } }   // bannered
        create("prod") { }                              // untouched
    }
}
```

A variant gets a banner **only** if `text` was set for it. There is no `enabled` flag, so there is no
way to forget one and ship a marked release.

No imports are needed, in Kotlin or in Groovy. `corner` takes `topLeft`, `topRight`, `bottomLeft`
or `bottomRight` directly; the `BannerCorner` enum is public too, if you would rather name it
explicitly.

### Precedence

Build type beats product flavor beats the project-level block — the same rule AGP uses for
everything else.

```kotlin
android {
    iconBanner { text = "UNRELEASED" }               // every variant, unless overridden
    productFlavors {
        create("dev") { iconBanner { text = "DEV" } }
        create("prod") { iconBanner { text = null } } // opts out of the inherited text
    }
    buildTypes {
        named("debug") { iconBanner { text = "DEBUG" } }   // wins over the flavor
    }
}
```

### Build metadata in the banner

`text` accepts a `Provider<String>`, which is only read when the icon is actually generated:

```kotlin
val gitSha = providers.exec { commandLine("git", "rev-parse", "--short", "HEAD") }
    .standardOutput.asText.map { it.trim() }

iconBanner { text = gitSha.map { "DEV $it" } }
```

Assigning a provider enables the banner even before its value is known. Note that under the
configuration cache, a provider may still be resolved during configuration on builds where the
generate task is in the task graph.

## Options

| Property | Type | Default | Notes |
|---|---|---|---|
| `text` | `String`, `Provider<String>`, `null` | unset | Unset means no banner. `null` clears an inherited value. `""` gives an empty ribbon. Rendered verbatim. |
| `color` | `String` | `#FF0000` | Hex, or a `@color/…` / `?attr/…` reference. Alpha is supported. |
| `textColor` | `String` | `#FFFFFF` | Same forms. |
| `corner` | `BannerCorner` | `topLeft` | `topLeft`, `topRight`, `bottomLeft`, `bottomRight`. |
| `height` | `Int` | `20` | Band width as a percentage of the icon's edge. The only size knob; text auto-fits. |
| `font` | `String` | `Roboto Mono` | Any Google Fonts family. |
| `weight` | `Int` | `700` | Must be a weight the family actually offers. |
| `italic` | `Boolean` | `false` | |

## How it works

The plugin reads `android:icon` from your manifest, follows the adaptive icon to its foreground
vector, and writes a bannered copy into a generated resource directory under the **same** resource
name. AGP's resource merger orders generated resources last, so the copy wins. Your source tree is
never modified.

Themed icons are handled properly rather than ignored. A monochrome layer keeps only alpha, so an
overlaid ribbon would render as one solid unreadable wedge. The plugin instead clips the icon content
away from the band and punches the text out of the ribbon as transparent holes, emitting that under a
separate name and redirecting `<monochrome>` at it.

Geometry is measured against the adaptive-icon **safe zone**, not the full 108dp canvas — a launcher
masks the icon to a circle, and text sized to the canvas gets sheared off at the rim.

## Fonts

Faces are downloaded from Google Fonts on demand and cached in
`~/.gradle/caches/android-icon-banner/fonts`, shared across projects and surviving `clean`. An
incremental build makes no network requests. `--offline` uses the cache or fails naming the URL it
needed.

Set `iconbanner.fontCacheDir` to relocate the cache, which is useful for a warmed, restorable cache
on CI.

The rendered glyph outlines are embedded in your app. Google Fonts are typically OFL-licensed, so
check the licence of any family you ship.

## Requirements and limitations

- **AGP 9+ and Gradle 9+.** No support for the AGP 8 line.
- **Application modules only.** Inert in library and dynamic-feature modules, so applying it from a
  convention plugin across every module is safe.
- **Vector icons only.** Legacy raster mipmaps (`mipmap-*/ic_launcher.webp`) are skipped, so devices
  below API 26 show the unmodified icon. If a variant asks for a banner and there is no vector at
  all, the build fails rather than silently shipping an unmarked icon.
- Overriding the foreground by name affects **every** use of that drawable, not just the launcher
  icon — a splash screen pointing at the same resource also gets the banner.

## Development

```bash
./gradlew -p plugin build          # plugin and its tests
./gradlew :app:assembleDevDebug    # demo app, bannered
./gradlew :app:assembleProdDebug   # demo app, untouched
```

`:app` exists only as a demo and manual visual check. The design and the reasoning behind it are in
[`specs/`](specs/icon-banner-gradle-plugin.md).

To publish a build for testing:

```bash
./gradlew -p plugin publishAllPublicationsToGitHubPackagesRepository
./gradlew -p plugin publishToMavenLocal      # or just locally
```

## Credits

Modelled on a [browser-based banner generator](https://bleeding182.github.io/tools/banner_generator.html)
that produced the same ribbon as copy-pasteable XML.

## License

[MIT](LICENSE)
