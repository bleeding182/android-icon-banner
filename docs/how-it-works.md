# How it works

Background for the curious, and for anyone debugging a build. None of this is needed to use the
plugin — see the [README](../README.md) for that. Design decisions and their reasoning live in
[`specs/`](../specs/icon-banner-gradle-plugin.md).

## Overriding the icon

The plugin reads `android:icon` and `android:roundIcon` from the variant's source manifests. It
follows an adaptive icon to its foreground vector — or banners a plain `<vector>` launcher icon
directly, or the bitmaps behind a legacy one, whichever you have — and writes the bannered copy into
a generated resource directory under the **same** resource name. AGP's resource merger orders
generated resources last, so the copy wins over the one in `main`, in a flavor, or in a build type.

Nothing is assumed on your behalf. An attribute you do not declare names no icon, so a project without
`android:roundIcon` gets no round icon bannered even if `mipmap/ic_launcher_round` exists — Android
populates `roundIconRes` from that attribute alone, so it is artwork no launcher loads. A name you *did*
declare has to resolve to something, or the build fails. Existing icons are modified and no new ones are
invented: every bannered file lands in the qualifier folder its source came from, so `anydpi` stays
`anydpi` and a density you do not ship stays unshipped.

Those attributes are read at AGP's own source-set precedence, so a flavor's declaration wins over one
in `main`. Merger directives are not interpreted, so `tools:remove="android:roundIcon"` in a flavor does
not hide a `roundIcon` declared in `main` — a project whose variants differ in which icons they *have* is
better off declaring them per flavor than removing them.

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

Still two, however many banners the variant carries: they both work on the same icon resources, so a
task per banner would only put several writers in one generated directory. The font task fetches
every face the variant's banners ask for into a directory of its own, one file per *distinct* family,
weight and slant, so two banners sharing a face cost one download. The generate task paints all of
them onto each icon file and writes it once.

Both are cacheable and up-to-date-checked, so an unchanged build does no work and makes no network
requests.

## Geometry

A launcher masks the icon to a circle, squircle or teardrop of its choosing, so a banner placed
against the full 108dp canvas gets sheared off at the rim. Two constants keep it inside:

- The band's **centre line** sits where `position` puts it, measured as a distance from the icon's
  centre: `position` is that distance as a percentage of the safe zone's radius, so 0 is the middle
  of the icon and 100 is the point at which no text fits. The default of 65 is chosen so that at the
  default style the band's corner-side edge still lands inside the narrowest mask a launcher is
  likely to apply, while the inner edge stays clear of the middle of the icon.
- The **text length** budget is the chord of that centre line across the adaptive-icon safe zone —
  the 66dp circle of the 108dp canvas — so long text shrinks rather than running out under the mask.
  In terms of the setting it is `2r · √(1 − position²)`, which is what makes 100 an endpoint rather
  than a convention, and what makes pushing a banner out cost text size so quickly.

Everything else follows from the text. `maxTextSize` is the cap height the text would like to be,
as a percentage of the icon's shorter viewport edge; the text is drawn at that size, or smaller if
the length budget cannot take it, or smaller again if `position` has pushed the band far enough out
that half a cap height either side of it would leave the safe zone. The band is then `lineHeight`
times whatever the text ended up
as — measured across the band, which is the same direction a cap height is measured in — so it hugs
the text instead of the text rattling around inside it, and the clearance above and below the glyphs
is simply what `lineHeight` left over. There is no font-size knob and no separate padding knob.

`lineHeight` is cosmetic and nothing else. It plays no part in the fit, and it does not restrict
`maxTextSize`. Because the centre line does not depend on the thickness, extra thickness grows
symmetrically: the corner-side edge moves towards the corner, where a mask may simply decline to draw
it, and the inner edge moves towards the middle of the icon, which is a matter of taste. Neither
costs the text anything. Only the *text* has to survive the mask, and that — half a cap height either
side of the centre line, against the safe zone's rim — is what caps `maxTextSize` at 21. That ceiling
is the value at the default position and stays put as a sanity bound; past the default the real limit
tightens, and the text is quietly clamped to it rather than the build being failed.

**Two kinds of length, and they differ by √2.** The ribbon runs at 45°, so the quad's coordinates are
intercepts on the x and y axes, while thicknesses and clearances are true distances measured across
the band. Moving a 45° edge by `t` perpendicular moves its axis intercept by `t * √2`. The two were
confused once, in the direction that is hard to see: `lineHeight` was written straight into the axis
offsets, so every band was drawn `1/√2` — 71% — of the thickness asked for. At the default that left
the glyphs 3% of their own height clear at each edge instead of 25%, which reads as text touching the
ribbon's edges. Lengths in the code that are axis-measured now say so in their names, and there is
exactly one place that converts between the two.

The band's position is deliberately *not* derived from its width. It used to be — the band was
anchored on its corner-side edge and grew inwards — and that quietly made the band width buy ribbon
position: a thicker band sat further in, where the chord across the safe zone is longer, so it also
bought text room. Sizing the band from the text with that coupling in place runs the loop backwards
and collapses: smaller text, thinner band, band drifts towards the corner, chord shortens, smaller
text again. Making the centre line an input breaks the loop, and the length budget then no longer
depends on the text at all, so the size is a division rather than a search. It is also why `position`
is an absolute scale rather than a nudge measured in band thicknesses, which would rebuild the loop
exactly.

**Only opposite corners are disjoint.** Where the centre line sits also decides which pairs of
banners can share an icon, and the answer is narrower than it looks. At the default style a
top-left band covers `x + y ∈ [0.58s, 0.86s]` of the shorter edge `s`, and a top-right band covers
`(s − x) + y` over the same range; the two strips cross at `(0.5s, 0.22s)`, which is `0.28s` from the
icon's centre against a mask at `0.33s`. The crossing is drawn, not masked away. So top-left with
bottom-right and top-right with bottom-left never touch, and every other pairing overlaps somewhere
near the middle of the icon.

Banners are painted in `z` order, lowest first, with declaration order breaking ties — so the highest
`z` is the one that stays readable where two of them cross. Two banners in the *same* corner draw a
build warning naming both, because that is an arrangement nobody chooses on purpose; adjacent corners
do not, because choosing two different corners is a choice.

## Themed icons

A monochrome layer keeps only alpha. Everything opaque is painted one system colour, so an overlaid
ribbon would render as a single unreadable wedge — band and text both solid, nothing to tell them
apart.

The plugin instead clips the icon content away from the band and punches the text out of the ribbon
as transparent holes, using an `evenOdd` fill. The text is a genuine cutout, so it stays legible
whatever tint the system picks.

Alpha being all that survives, it is also all a banner gets to choose here: `monochromeAlpha` sets
the band's, and the tint then comes out lighter or heavier at the same hue. `color` and `textColor`
never reach this layer.

With several banners the clips **nest**. `VectorDrawable` unions two `<clip-path>` elements in one
`<group>` rather than intersecting them, and each of these clips is *everything outside* one band —
so a single group carrying both would keep the artwork everywhere except where the two bands cross,
which is the opposite of what either clip asked for. Each banner therefore wraps whatever is at the
root in a group of its own: N nested groups, one clip each, which does intersect. Only once all of
them are in place are the even-odd ribbon paths appended at the root, in paint order. A ribbon
appended earlier would be swallowed by the next banner's group and cut out by the very clip meant to
protect the artwork from it.

All of that is the vector rewrite. A themed *bitmap* reaches the same cutout in pixels instead — see
[Raster icons](#raster-icons).

Where that output lands depends on your icon. When `<monochrome>` has its own drawable it is
bannered in place, under its own name, and the adaptive icon is left alone. When `<monochrome>` and
`<foreground>` point at the *same* drawable — the Android Studio template does this — the two need
different treatment, so the monochrome version is emitted under a separate name and the adaptive
icon is rewritten to redirect `<monochrome>` at it.

## Fonts

Faces are downloaded from Google Fonts on demand and cached in
`~/.gradle/caches/android-icon-banner/fonts`, shared across projects and surviving `clean`. The
first build that uses a family fetches it; nothing after that hits the network.

A variant asks for as many faces as its banners have distinct combinations of family, weight and
slant — not one. They are fetched into the variant's font directory under a name derived from the
face rather than from the banner that wanted it, so two banners in the same face are handed the same
file instead of two copies of the same bytes.

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

Legacy raster mipmaps (`mipmap-*/ic_launcher.webp`) are bannered too, so a device below API 26 shows a
marked icon rather than the production one. The file is decoded, the banner is composited straight into
its pixels, and the result is written under the same resource name in the same qualifier folder — every
density gets its own bannered copy.

The output is always a **PNG**, because the JDK can write that and not WebP: `mipmap-hdpi/ic_launcher.webp`
comes back as `mipmap-hdpi/ic_launcher.png`. The extension is not part of a resource's identity, so that
is still a clean override and the original webp is never compiled into the APK.

A standalone legacy icon is drawn by the launcher with no mask of its own, so the band is clipped to the
icon's **own alpha**: a round `ic_launcher_round` keeps its silhouette instead of growing a triangle
where the corner used to be. A bitmap that an adaptive icon's layer points at is not clipped that way —
the system masks that layer already, and an adaptive foreground is usually a logo on a large transparent
surround, where clipping to the artwork would erase most of the band.

Themed bitmaps work as the vector ones do, by subtraction rather than by an `evenOdd` fill: the band is
cleared out of the pixels and filled back in at `monochromeAlpha` with the glyphs left as holes. The
cutout comes out the same, counters and all.

Reading WebP needs a decoder the JDK does not ship. The plugin fetches one
(`com.twelvemonkeys.imageio:imageio-webp`, about 580 KB) from your project's own repositories, and only
once the JDK has actually failed on one of your bitmaps — an icon that is all vectors or all PNG never
asks for it. See the README's [Limitations](../README.md#limitations) for what that means for a project
with no `mavenCentral()`.

A nine-patch is skipped, and so is a file no available reader can decode; each says so with a warning
naming the file. Only when *nothing* in an icon could be bannered — no vector, no usable bitmap — does
the build fail rather than silently shipping an unmarked icon.

One thing to expect rather than to debug: the sizes are percentages of the icon's own edge, and a legacy
icon's whole edge is visible where an adaptive icon's 108 units are cropped to a 72-unit mask. The same
`maxTextSize` therefore reads about 1.5× smaller on a legacy icon — 6.2dp against 9.4dp at the default —
which is exactly the treatment a plain non-adaptive `<vector>` icon has always had.

The legibility warning does not correct for it either, and deliberately: it is measured against the
adaptive figure throughout, so an old icon that is simply small does not produce a warning on every
build of every project that has one. The banner goes on the icon you have.

## Versions

The plugin is built and tested against AGP 9.3 and Gradle 9, uses variant APIs the AGP 8 line does
not have, and is compiled for JDK 17 — the floor AGP 9 itself sets. An older AGP fails early with a
message naming the minimum and what it found, rather than an opaque linkage error.

A variant with the `androidResources` build feature turned off has nowhere to put a generated
resource directory. That case logs a warning and produces no banner instead of failing the build.
