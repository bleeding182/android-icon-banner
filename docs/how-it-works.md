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
against the full 108dp canvas gets sheared off at the rim. Two constants keep it inside:

- The band's **centre line** sits at a fixed fraction of the icon — 0.72 of the shorter edge,
  measured along each axis from the corner — chosen so that at the default style the band's
  corner-side edge still lands inside the narrowest mask a launcher is likely to apply, while the
  inner edge stays clear of the middle of the icon.
- The **text length** budget is the chord of that centre line across the adaptive-icon safe zone —
  the 66dp circle of the 108dp canvas — so long text shrinks rather than running out under the mask.

Everything else follows from the text. `maxTextSize` is the cap height the text would like to be,
as a percentage of the icon's shorter viewport edge; the text is drawn at that size, or smaller if
the length budget cannot take it. The band is then `lineHeight` times whatever the text ended up
as, so it hugs the text instead of the text rattling around inside it, and the clearance above and
below the glyphs is simply what `lineHeight` left over. There is no font-size knob and no separate
padding knob.

The band's position is deliberately *not* derived from its width. It used to be — the band was
anchored on its corner-side edge and grew inwards — and that quietly made the band width buy ribbon
position: a thicker band sat further in, where the chord across the safe zone is longer, so it also
bought text room. Sizing the band from the text with that coupling in place runs the loop backwards
and collapses: smaller text, thinner band, band drifts towards the corner, chord shortens, smaller
text again. Pinning the centre line breaks the loop, and the length budget then no longer depends
on the text at all, so the size is a division rather than a search.

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

The route is the public CSS endpoint, `fonts.googleapis.com/css2`, whose response is scraped for the
`fonts.gstatic.com` URL of the face, which is then downloaded and checked for TrueType magic before
it is cached. Only `https` URLs on `fonts.gstatic.com` are accepted, so a tampered CSS response or
cache entry cannot redirect the download elsewhere.

That endpoint returns woff2 to browsers it recognises and plain TTF to everything else, which is why
the plugin identifies itself honestly and gets TTF: a woff2 would need a brotli decoder, and
`java.awt.Font` cannot read one anyway.

The obvious alternative — the raw TTFs in the `google/fonts` repository — is not used because most
families there are now published as variable fonts, and `java.awt.Font` cannot instance a weight
axis, which would break the `weight` option.

`--offline` uses the cache or fails naming the URL it needed. Set the `iconbanner.fontCacheDir`
Gradle property to relocate the cache, which is useful for a warmed, restorable cache on CI — use an
absolute path, as a relative one resolves against the module the plugin is applied to.

The build fails if the chosen font has no glyph for a character in the banner text, rather than
rendering a row of tofu.

The rendered glyph outlines are embedded in your app as path data — the font file itself is not
shipped. Under the OFL that path data is not Font Software and you owe nothing for it; the README's
[Fonts and licensing](../README.md#fonts-and-licensing) section has the detail, including the
Apache-2.0 families where the question is less settled.

## Raster icons

Legacy raster mipmaps (`mipmap-*/ic_launcher.webp`) are skipped, so a device below API 26 shows the
unmodified icon. If a variant asks for a banner and there is no vector icon at all, the build fails
rather than silently shipping an unmarked one.

## Versions

The plugin is built and tested against AGP 9.3 and Gradle 9, uses variant APIs the AGP 8 line does
not have, and is compiled for JDK 17 — the floor AGP 9 itself sets. An older AGP fails early with a
message naming the minimum and what it found, rather than an opaque linkage error.

A variant with the `androidResources` build feature turned off has nowhere to put a generated
resource directory. That case logs a warning and produces no banner instead of failing the build.
