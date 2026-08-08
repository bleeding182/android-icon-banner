# How it works

Background for the curious, and for anyone debugging a build. None of this is needed to use the
plugin — see the [README](../README.md) for that. Design decisions and their reasoning live in
[`specs/`](../specs/icon-banner-gradle-plugin.md).

## Overriding the icon

The plugin reads `android:icon` and `android:roundIcon` from the merged manifest, falling back to
the conventional `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round` when neither is declared. It
follows an adaptive icon to its foreground vector — or banners a plain `<vector>` launcher icon
directly, if that is what you have — and writes the bannered copy into a generated resource
directory under the **same** resource name. AGP's resource merger orders generated resources last,
so the copy wins over the one in `main`, in a flavor, or in a build type.

Your source tree is never modified. Remove the plugin, or the `text` for a variant, and the original
icon is back — there is nothing to clean up outside `build/`.

Because the override is by resource name, it affects **every** use of that drawable, not just the
launcher icon. A splash screen pointing at the same foreground also gets the banner.

The generated files land in `app/build/generated/res/generate<Variant>IconBanner/`. Two tasks per
bannered variant, in the `icon banner` group:

```
download<Variant>IconBannerFont
generate<Variant>IconBanner
```

Both are cacheable and up-to-date-checked, so an unchanged build does no work and makes no network
requests.

## Geometry

A launcher masks the icon to a circle, squircle or teardrop of its choosing, so a banner placed
against the full 108dp canvas gets sheared off at the rim. Two separate constants keep it inside:

- The band's corner-side edge sits at a fixed fraction of the icon (0.60), chosen against the
  narrowest mask a launcher is likely to apply.
- The **text length** budget is clamped to the adaptive-icon safe zone — the 66dp circle of the
  108dp canvas — so long text shrinks rather than running out under the mask.

`height` is the width of the band, as a percentage of the icon's shorter viewport edge; the band
runs diagonally, so its "height" is the distance across it. Text is centred and scaled to fit the
length that remains, which is why there is no font-size knob.

## Themed icons

A monochrome layer keeps only alpha. Everything opaque is painted one system colour, so an overlaid
ribbon would render as a single unreadable wedge — band and text both solid, nothing to tell them
apart.

The plugin instead clips the icon content away from the band and punches the text out of the ribbon
as transparent holes, using an `evenOdd` fill. The text is a genuine cutout, so it stays legible
whatever tint the system picks.

Where that output lands depends on your icon. When `<monochrome>` has its own drawable it is
bannered in place, under its own name, and the adaptive icon is left alone. When `<monochrome>` and
`<foreground>` point at the *same* drawable — the Android Studio template does this — the two need
different treatment, so the monochrome version is emitted under a separate name and the adaptive
icon is rewritten to redirect `<monochrome>` at it.

## Fonts

Faces are downloaded from Google Fonts on demand and cached in
`~/.gradle/caches/android-icon-banner/fonts`, shared across projects and surviving `clean`. The
first build that uses a family fetches it; nothing after that hits the network.

`--offline` uses the cache or fails naming the URL it needed. Set the `iconbanner.fontCacheDir`
Gradle property to relocate the cache, which is useful for a warmed, restorable cache on CI — use an
absolute path, as a relative one resolves against the module the plugin is applied to.

The build fails if the chosen font has no glyph for a character in the banner text, rather than
rendering a row of tofu.

The rendered glyph outlines are embedded in your app as path data — the font file itself is not
shipped. Google Fonts are typically OFL-licensed, so check the licence of any family you ship.

## Raster icons

Legacy raster mipmaps (`mipmap-*/ic_launcher.webp`) are skipped, so a device below API 26 shows the
unmodified icon. If a variant asks for a banner and there is no vector icon at all, the build fails
rather than silently shipping an unmarked one.

## Versions

The plugin is built and tested against AGP 9.3 and Gradle 9, and uses variant APIs that the AGP 8
line does not have. There is no version guard, so an older AGP fails with a linkage error rather
than a readable message.

A variant with the `androidResources` build feature turned off has nowhere to put a generated
resource directory. That case logs a warning and produces no banner instead of failing the build.
