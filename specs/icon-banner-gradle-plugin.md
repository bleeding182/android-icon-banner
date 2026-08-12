# Icon Banner Gradle Plugin

Status: implemented
Plugin id: `io.github.bleeding182.iconbanner`

## Problem Statement

I have several build variants of the same app installed on one device at the same time — a dev
build, a staging build, sometimes a release candidate. They all use the same launcher icon, so I
cannot tell them apart in the launcher, in the recents switcher, or in the app info screen. I open
the wrong one, file bugs against the wrong build, and demo the wrong build.

I already solved the visual half of this with a browser tool that generates a corner ribbon and
gives me two `<path>` snippets to paste into my foreground vector. But pasting is manual and
one-shot: the snippet lands in a resource that is shared by every variant, so I have to maintain
per-flavor copies of my launcher icon by hand and keep them in sync whenever the icon changes.
Worse, it is easy to forget to remove the banner and ship it to production.

I also want a bit of build metadata on the icon — a version number or a short git SHA — which a
hand-pasted static snippet cannot give me at all.

## Solution

A Gradle plugin that adds the banner during the build, per variant.

I apply the plugin, set a couple of style defaults in `android { iconBanner { } }`, and set `text`
on the flavors or build types that should be marked. The plugin finds my launcher icon on its own,
produces a bannered copy for exactly those variants, and leaves every other variant completely
untouched. Nothing in my source tree changes; my checked-in icon stays the clean production one.

Because the banner is generated at build time from real configuration, `text` can be a lazily
evaluated provider, so a git SHA or a CI build number can appear on the icon.

The laziness guarantee is narrower than it first looks, and the implementation confirmed it: a
provider is definitely not read on a build that does not request the icon. On a build that does,
the configuration cache finalizes task input providers when it stores its entry, so the provider may
still be resolved during configuration. That is the guarantee the plugin can actually make. The same
cause narrows a second promise the same way — see "The honest narrowing" under Rasterized icons, where
the configuration cache resolves the bitmap reader for a build that never needed it.

Themed (monochrome) icons are handled properly rather than ignored: monochrome only keeps alpha, so
a colored ribbon layered on top would render as a solid untextured wedge with unreadable text. The
plugin instead clips the icon content and punches the text out of the ribbon as holes.

## User Stories

### Core marking

1. As an Android developer, I want a DEV banner on my dev flavor's launcher icon, so that I can tell
   my dev build apart from production on a device that has both installed.
2. As an Android developer, I want my release variant's icon to be byte-for-byte unchanged, so that
   I can be certain a debug marker can never reach the Play Store.
3. As an Android developer, I want to set the banner text on a product flavor, so that the marking
   follows the flavor rather than the build type.
4. As an Android developer, I want to set the banner text on a build type, so that I can mark every
   debug build regardless of flavor.
5. As an Android developer, I want build-type text to win over flavor text, so that the precedence
   matches every other setting in the Android DSL and I do not have to learn a second rule.
6. As an Android developer, I want to set style once in `android { iconBanner { } }` and only text
   per flavor, so that I am not repeating colors and fonts in every flavor block.
7. As an Android developer, I want to explicitly clear an inherited banner on one flavor, so that I
   can set a project-wide default and carve out an exception.
8. As an Android developer, I want a variant with no text configured anywhere to get no banner, so
   that opting in is always a deliberate act.
9. As an Android developer, I want an empty string to produce a ribbon with no text, so that I can
   use a plain color flash as a subtler marker.

### Zero configuration

10. As an Android developer, I want the plugin to find my launcher icon without being told where it
    is, so that adopting it is one `text = "DEV"` line.
11. As an Android developer with a non-standard icon resource name, I want the plugin to read the
    name from my manifest, so that it works without me duplicating the name in a second place.
12. As an Android developer, I want my round icon to be bannered too, so that launchers requesting
    the round variant do not silently show an unmarked icon.
13. As an Android developer, I want density- and API-qualified copies of my icon drawables to all be
    bannered, so that the marking does not disappear on a subset of devices.
14. As an Android developer, I want my adaptive icon's background layer left alone, so that only the
    foreground gains the ribbon and the icon keeps its intended shape and backdrop.
15. As an Android developer who never migrated to adaptive icons, I want my plain vector icon
    bannered too, so that the plugin is not gated on an unrelated migration.
16. As an Android developer, I want anything in my icon XML that the plugin does not understand to
    survive into the output, so that adopting the plugin cannot quietly drop part of my icon.
17. As an Android developer in a non-English locale, I want identical output to my colleagues, so
    that the icon does not break purely because of my machine's number formatting.

### Themed icons

18. As an Android developer, I want the themed/monochrome icon to also carry the banner, so that
    users with themed icons enabled can still tell my builds apart.
19. As an Android developer, I want the monochrome banner to remain legible, so that it does not
    collapse into a solid tinted wedge the way a naive overlay would — and, with two banners, to
    shade one of them, so that they stay distinguishable where the system gives everything one
    colour.
20. As an Android developer whose icon has no monochrome layer, I want the plugin to simply skip it,
    so that I am not forced to add one.

### Dynamic text

21. As an Android developer, I want to put my version name in the banner, so that I can see at a
    glance which build a tester is running.
22. As an Android developer, I want to put a git SHA in the banner, so that I can trace a screenshot
    back to an exact commit.
23. As an Android developer, I want a provider-based text to be evaluated lazily, so that the git
    command does not run during configuration on every build.
24. As an Android developer, I want assigning a provider to enable the banner even before its value
    is known, so that enablement never depends on when a value happens to resolve.

### Appearance

25. As an Android developer, I want to choose which corner the ribbon sits in, so that it does not
    cover the distinctive part of my icon.
26. As an Android developer, I want to set the ribbon and text colors, so that different flavors can
    be distinguished from each other and not just from production.
27. As an Android developer, I want to use colors with alpha, so that I can apply a translucent wash
    rather than an opaque block.
28. As an Android developer, I want to pick any Google Font, so that the banner matches my brand or
    is simply more readable than the default.
29. As an Android developer, I want to pick a font weight and italic, so that a short marker can be
    bold and legible at launcher size.
30. As an Android developer, I want one knob for banner height, so that I can make the marking
    louder or subtler without learning a coordinate system.
31. As an Android developer, I want the text auto-fitted and centered, so that "DEV" and "STAGING"
    both look right without me tuning a font size and rebuilding to check.
32. As an Android developer, I want my text rendered exactly as I typed it, so that lowercase or
    mixed case is preserved if that is what I asked for.
33. As an Android developer, I want the same height value to look identical regardless of my
    foreground vector's viewport size, so that the setting is portable between projects.

### Build behavior

34. As an Android developer, I want the banner generated into the build directory only, so that my
    source tree and my git status stay clean.
35. As an Android developer, I want a rebuild with no changes to skip the work, so that the plugin
    does not slow down my edit-build-run loop.
36. As an Android developer, I want the plugin to be configuration-cache compatible, so that I do
    not have to disable a feature my build already relies on.
37. As an Android developer, I want the font downloaded once and cached across projects, so that
    only the very first build pays for it.
38. As an Android developer, I want an incremental build to make no network calls at all, so that I
    can work on a train.
39. As an Android developer building offline for the first time, I want a failure that names the URL
    it needed, so that I know exactly what to warm the cache with.
40. As an Android developer, I want the build to fail loudly if I asked for a banner and there was
    nothing in my icon to put it on, so that I never get a silently unmarked build.
41. As an Android developer whose icon also has legacy raster mipmaps, I want those bannered as well,
    so that a device below API 26 shows a marked icon rather than the production one.
42. As an Android developer, I want the plugin to do nothing in library modules, so that applying it
    from a convention plugin across all modules is safe.
43. As an Android developer whose launcher icon is only bitmaps, I want it bannered too, so that the
    plugin is not gated on migrating to adaptive icons.
44. As an Android developer, I want my round bitmap icon's band clipped to the icon's own shape, so
    that a round icon does not grow a triangle where the corner used to be.
45. As an Android developer with a bitmap the plugin cannot read, I want a warning naming the file
    rather than a failed build, so that one odd density does not stop me building.

### Adoption

46. As an Android developer, I want to apply the plugin by id from a normal repository, so that I do
    not have to vendor it.
47. As a contributor to the plugin, I want a sample app in the repo, so that I can see the result on
    a real launcher before publishing.
48. As a contributor to the plugin, I want golden-file tests for the generated XML, so that a
    geometry regression is caught without me installing an APK.
49. As a contributor to the plugin, I want the tests to run without network, so that CI is not
    coupled to a third-party font service.

## Implementation Decisions

### Compositing strategy: rewrite, do not overlay

The plugin reads the existing foreground vector and produces a **rewritten copy**, rather than
layering a separate banner drawable on top via a layer-list.

The deciding factor is monochrome. A themed icon keeps only alpha, so an overlaid opaque ribbon
plus opaque text produces one solid wedge with no readable text. Producing a correct monochrome
banner requires clipping the icon content away from the ribbon area and punching the text out of
the ribbon as holes — both of which mean editing the icon's own path structure. Since a rewrite is
required for the monochrome case anyway, the colored case uses the same mechanism rather than
maintaining two.

Rewriting is safe precisely because it happens inside the build: the checked-in resource is never
modified, only a generated copy.

### Two distinct rewrites

**Colored foreground.** Two `<path>` elements are appended to the existing vector's root: the ribbon
quad, then the pre-transformed text outline. Appending at root level draws them above all existing
content and works regardless of any groups, transforms or clip-paths the original already contains.

**Monochrome.** All existing root children are wrapped in a single `<group>` carrying an
inverse-ribbon `<clip-path>`, so icon content cannot bleed into the ribbon area. The ribbon and text
are emitted as one combined path with `android:fillType="evenOdd"` and a white fill, making the
glyphs transparent holes. This mirrors the approach already proven in the browser generator.

#### Superseded: the white fill's alpha became `monochromeAlpha`

The fill was fixed at `#FFFFFFFF` on the reasoning that the system replaces the RGB with its tint
and keeps the alpha, so nothing about the colour was worth exposing. That reasoning stops one step
short: the alpha *is* kept, and it is therefore the only appearance a themed banner can choose. With
one banner it hardly matters; with two it is the difference between telling them apart and not, since
`color` — the thing that separates them everywhere else — has no effect on this layer at all.

So `monochromeAlpha` is a percentage, 0..100, default 100, and the generator turns it into the fill's
alpha byte. Deliberately not a colour: accepting `#CCFFFFFF` here would invite setting an RGB that
the system then throws away, and a value that is silently ignored is worse than one that does not
exist. The name matches the `<monochrome>` tag the value acts on rather than the "themed icon" the
user sees, because that is what the docs have to explain either way.

The whole 0..100 range is allowed, including 0 — a band cut out of the icon and not filled back in is
a coherent look, not a mistake, and it is the only value that needs an explanation. Adding it made
`BannerGeometryBounds` the wrong name for the object that checks the numeric knobs, since alpha is
not geometry; it is now `BannerBounds`.

Where `<foreground>` and `<monochrome>` reference the same drawable — the default Android Studio
template does exactly this — one name cannot hold two different outputs. The monochrome result is
therefore written under a new name and the adaptive-icon XML is rewritten to redirect its
`<monochrome>` reference.

### Rasterized icons

A bitmap backing the launcher icon is decoded, the banner is composited into its pixels with Java2D,
and the result is written back as **PNG** whatever went in, because the JDK has no WebP *writer*. Same
resource name, same qualifier folder, one different extension.

Pixels rather than a `<layer-list>` stacking a banner drawable over the bitmap, and that alternative
was considered seriously. It fails on the same ground as "rewrite, do not overlay" above:
**monochrome**. A layer-list cannot clip a bitmap to a path, so a themed icon's glyph holes fill
straight back in from the bitmap beneath — the exact failure clip-and-punch exists to prevent. It
would also have needed a verbatim renamed copy of every density file, since a layer-list cannot
reference the name it overrides, so both copies would sit in the APK.

#### Delivery: one extension apart, settled by spike

Against AGP 9.3.1 and Gradle 9.5, in the same spirit as the same-name spike below. A generated
`mipmap-hdpi/ic_launcher.png` cleanly overrides `main`'s `mipmap-hdpi/ic_launcher.webp`. The merger's
own state file shows why: the merge key is name, type and qualifiers, the extension is not part of it,
and the data sets are ordered `main`, build type, then `generated`. The original webp is not merely
outranked — it is never compiled.

Three further findings, two of which shaped the code:

- **Mixed extensions across the densities of one resource are legal.** Generating only `hdpi` as PNG
  while `mdpi` stays webp packages both and resolves correctly, so a partially rewritten icon is not
  a case to design around.
- **Nothing warns if the plugin writes the wrong qualifier folder.** No merger message, no lint
  finding: the original is silently packaged instead and the banner simply never appears. That is why
  the output reuses the source's own qualifier string verbatim, and why a test pins it.
- Release builds re-encode the result — `crunchPngs` palette-quantizes it losslessly and
  `optimizeReleaseResources` renames it — so no test may assert that the packaged bytes equal the
  generated bytes. It stays a PNG; nothing is converted back to webp on the way.

#### The silhouette clip, the one genuinely new piece of geometry

A **standalone legacy raster icon** — the whole 48dp icon, which a launcher draws with no mask — is
painted with `AlphaComposite.SrcAtop`, so the band lands only where the icon already has alpha. That
is the raster analogue of the mask an adaptive icon gets for free; without it the band runs out to the
canvas corner and gives a round icon a floating triangle.

A **bitmap that an adaptive icon's layer points at** is painted plain source-over instead. The system
masks that layer itself, and an adaptive foreground is typically a logo floating on a large
*transparent* surround, so `SrcAtop` there would erase almost the whole band and leave the ribbon
showing only over the logo. The distinction cannot be inferred from the image: the caller states it,
and it is asserted in both directions.

Monochrome in pixels is `Clear` over the ribbon quad, then `Area(quad) − Area(glyphs)` filled white at
`monochromeAlpha` — the same clip-and-punch, with a subtraction standing in for a fill rule that has
no raster counterpart. That is a reproduction and not an approximation, which is worth stating because
"even-odd has no raster counterpart" reads like a compromise was accepted. `Area` resolves the glyph
outline under the outline's *own* non-zero winding before the subtraction, so what is taken out of the
quad is exactly the region the vector's `evenOdd` fill leaves as holes — counters included: the
even-odd rule's third crossing fills the inside of a `D`, and so does not subtracting it.

The two phases run as two passes across the banners, for a
different reason than the vector's nested groups — in a single loop the second banner's clear would
eat the first banner's fill wherever the bands overlap. One deliberate divergence from even-odd: a
glyph part protruding past the band is not filled white, where the fill rule would fill it. The band
is `lineHeight` times the text's own ink height and centred on it, so that is reachable only below a
`lineHeight` of about 1.0 — and 1.0 is the floor the bounds enforce, so no build script can reach it.

#### Known follow-up: no sizing knob of its own, and the 1.5× skew

Deliberately no raster-specific sizing, because the configuration has to mean the same thing whatever
the icon is. A bitmap declares no viewport, so its pixel size is one, and everything in the geometry
is proportional: a 48px mdpi icon and a 192px xxxhdpi one get identical-looking banners out of one
value.

The consequence has to be recorded honestly, because it is visible when the two icons sit side by
side. A legacy raster's whole edge is visible icon, while the adaptive canvas is 108 units cropped to
a 72-unit mask, so identical percentages read about **1.5× smaller** on the legacy icon: the cap height
`maxTextSize = 13` asks for is 9.4dp on the adaptive icon and 6.2dp on a 48dp legacy one. The
legibility warning inherits the same skew, since `LAUNCHER_DP_PER_EDGE = 72` is the adaptive figure
and 1.5× optimistic for a legacy icon — so those icons under-warn against the 4dp floor.

Left as it is, and the argument is precedent rather than indifference: this is exactly the treatment a
plain non-adaptive `<vector>` icon already gets, which is also drawn unmasked at its full edge. A
mask-aware scale for both was considered; it would move every golden file and grow every current
user's banner by half. A known follow-up, not a bug.

#### Colour values must be literals

A bitmap fill needs an actual ARGB value the plugin can parse itself, so the four accepted forms are
`#RGB`, `#ARGB`, `#RRGGBB` and `#AARRGGBB` — nothing else, on every kind of icon alike. One rule for both
icon forms is the point: a configuration value cannot mean two things depending on which kind of icon it
lands on.

#### The WebP reader, which the consuming build resolves

The JDK has none: on 17 and 21 `ImageIO.getReaderFormatNames()` offers JPEG, PNG, BMP, GIF, TIFF and
WBMP and nothing else. Android Studio generates the legacy mipmaps as WebP, so a reader has to come
from outside or the feature misses the case it exists for.

`com.twelvemonkeys.imageio:imageio-webp:3.14.0`, pinned. Pure Java with no native libraries —
verified rather than assumed, by looking for `.so`/`.dll`/`.dylib` payloads, `native` methods and
`loadLibrary` calls across all 396 classes of it and its five transitives. Reader-only SPI, six jars
totalling 580 KB with no third-party transitives of their own, Java 8 bytecode, BSD-3-Clause.
Verified by decoding the demo app's own icons, which are VP8 lossy with alpha in a separate `ALPH`
chunk, and a lossless VP8L file.

Rejected:

- `org.sejda.imageio:webp-imageio` is JNI, last published in 2020, and ships no Linux aarch64 native
  at all — precisely the silent platform failure a Gradle plugin cannot afford.
- `com.github.usefulness:webp-imageio` is JNI too. It does cover eleven platforms, and it is the
  fallback if the pure-Java constraint is ever dropped, at 3.3 MB.
- Apache Commons Imaging cannot decode WebP pixels at all: `WebPImageParser.getBufferedImage`
  unconditionally throws.

It is deliberately **not** an `implementation` dependency, which would land it on every consuming
buildscript's classpath. It is resolved in the consuming project from a resolvable, non-consumable
`iconBannerImageReaders` configuration whose `defaultDependencies` supplies the pinned coordinates, so
a declared version wins over them. That configuration is created **eagerly when the plugin is
applied**, not per variant: a build script's `dependencies { }` block runs long before AGP hands out
variants, so creating it per variant makes the override fail with "Configuration with name … not
found".

*When* it is resolved matters as much as where from, and the first implementation had it wrong: it
registered the extra readers before **every** bitmap decode. A project whose legacy mipmaps are plain
PNG then paid a dependency resolution for a WebP reader it has no use for, and *failed* wherever that
resolution could not reach a repository — a repository it never needed. The order is now the JDK's own
readers first and the configuration only once `ImageIO` has come back null. The cost is decoding one
192px icon twice on a build that does need the reader, which is nothing beside a resolution; the gain
is that a PNG-only or vector-only icon graph never asks at all, and the seam that can throw is only
ever reached for a file the JDK genuinely could not read.

#### Registration, and the trap behind it

`ImageIO`'s registry is built lazily, once, and cached per thread group. Established by experiment:
set the thread context class loader and *then* touch `ImageIO`, and the reader is visible; touch
`ImageIO` first and the reader is missing **permanently** while `ImageIO.read` silently returns null;
`scanForPlugins()` after setting the loader repairs it in every ordering. Inside a Gradle daemon there
is no telling what touched `ImageIO` first, so the loader is set, `scanForPlugins()` is called
unconditionally, and the previous loader is restored in a `finally`. The registered reader keeps
working after the restore, and a test pins that.

The loader is cached per jar set for the daemon's lifetime — deliberate, and the same lifetime an
ordinary dependency's loader would have — but the scan repeats on every call, because the registry is
per thread group and a cached "already registered" would silently mean no reader in a later build.
Registration is synchronized: two variants' generate tasks can run in parallel and `IIORegistry` is
not built for concurrent registration. The loader's parent is the platform class loader, so a stray
copy of the reader on some buildscript's classpath cannot win over the pinned one.

A Gradle `WorkerExecutor` with `classLoaderIsolation` was considered and rejected. The decode sits
deep inside the pure generator's call graph, so a worker would mean serialising the entire request
across a boundary for isolation that does not matter here, and it would move the task's logging and
failure reporting out of the task.

#### The task's two properties, which each look wrong alone

The reader classpath is `@Internal` and the pinned coordinates are an `@Input`. Neither makes sense on
its own: an `@InputFiles` classpath would resolve the configuration on every run of the task,
including for a project that decodes nothing, and an untracked classpath alone would let a change of
reader version go unfingerprinted. Also worth recording, because it reads like an oversight:
`defaultDependencies` does not run on `getAllDependencies()` in Gradle 9, so the input mirrors that
block's own rule — declared coordinates, else the pinned constant — rather than forcing resolution to
find out.

#### The honest narrowing: the configuration cache resolves it anyway

The intended guarantee was that a project whose icons are all vectors never resolves the reader and
never touches the network. **The configuration cache does not allow it.** It serialises a task's file
collections eagerly regardless of `@Internal`, so with a strict `setFrom(configuration)` a vector-only
build *fails at store time* — "error writing value of type 'DefaultConfigurableFileCollection' > Could
not resolve all files for configuration ':iconBannerImageReaders'" — in any project that declares only
`google()`. That would have been a hard regression for builds that never asked for a bitmap banner.

So the classpath is a **lenient** artifact view, and the actionable failure is raised when the
resolved set comes back **empty**, which is only ever checked once a bitmap the JDK could not decode is
in hand. What the plugin can therefore actually promise: without the configuration cache, resolution
genuinely waits for that first undecodable bitmap, and an icon graph of vectors or of PNGs never
resolves at all; with the cache on, the graph resolves once per stored entry, so about 580 KB is
fetched once per machine even for a vector-only project.

This is the same shape, and the same underlying cause, as the narrowing recorded in Solution about
lazily evaluated `text` providers — the configuration cache resolves what it has to store, so "at
execution time" becomes "during configuration, on the build that stores the entry". A reader who hits
one of these should be shown the other.

Leniency hides why resolution failed, so the failure message points at
`gradle dependencies --configuration iconBannerImageReaders`. It also **names the file** that could not
be decoded, and names both halves of the cause, because since the JDK's readers go first this failure
is only ever reached on a file the JDK genuinely failed on: either no reader could be fetched, or the
file itself is not the image it claims to be. The message says so — "if it reports nothing wrong,
suspect the file itself: a truncated or corrupt image fails here just the same." One hole remains and
is accepted: a *partial* resolution is not an empty one, so it surfaces as a class-loading error rather
than the tidy message.

#### Known follow-up: an unresolvable reader throws where an undecodable file only warns

The two failure policies disagree, and the disagreement is deliberate. An undecodable bitmap is a
warning and a skipped file; an unresolvable reader is a build failure. So a project that has one
genuinely corrupt bitmap **and** no repository serving the reader hard-fails, even though every other
density bannered fine — which is not what "skipped with a warning" promises.

Kept, on two grounds. Failing loudly when a dependency cannot be fetched is the defensible half: the
plugin cannot tell an offline build from a broken file, and silently shipping an unmarked density
because a repository was missing is the failure this plugin exists to prevent. And the case is far
narrower than it was before the readers moved behind the JDK's own — it needs a bitmap the JDK cannot
read *and* a resolution that comes back empty — while the message names the file and points at both
causes, so nobody is left guessing.

The alternative is a design change nobody has argued for yet: let `ensureReadersAvailable` *report*
rather than throw, so the Gradle layer's inability to fetch a reader becomes one more per-file skip
reason and the "every file skipped" rule decides whether the build fails. That is the right shape if
this ever bites someone. It also loses the actionable message on a build that is merely offline, which
is why it is not the default.

The road not taken, worth recording in case the priority ever flips: if "never touches the network"
matters more than using Gradle's dependency management, the font route works — fetch the jars over
HTTP into a shared cache under the Gradle user home at execution time — at the cost of hand-rolled
URLs and checksums.

#### Verified on the demo app

`:app:generateAdaptiveVectorDebugIconBanner` writes ten bannered PNGs —
`mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png` and the same five for `ic_launcher_round` —
each matching its source webp's dimensions (48, 72, 96, 144, 192), alongside the vector outputs.
`assembleAdaptiveVectorDebug` packages only the bannered PNGs, byte-identical to the generated files,
and no webp survives. `assembleProdDebug` still ships the original webp untouched. The round icon's
band is visibly clipped to its silhouette.

Two flavors added later cover the rest of the path, both confirmed in the packaged APK and by eye.
`legacyRaster` is an icon that is *only* bitmaps, and its `app_icon_round.png` is the clearest look at
the silhouette clip; `adaptiveRaster` is the case where the pixels sit behind an adaptive layer, whose
foreground keeps a full-width band across the transparent surround and whose monochrome PNG comes back
with the glyphs punched out and the artwork cleared from under the band. `adaptiveRaster` emits no
rewritten `anydpi-v26` XML at all, which is the visible sign of the other monochrome branch: a
`<monochrome>` with a drawable of its own is bannered in place and needs no redirect.

### Icon discovery: zero configuration

There is no DSL property naming the icon. The plugin reads `android:icon` and `android:roundIcon`
from the `<application>` element of the variant's source manifests. References are then resolved against
the variant's static resource directories — at AGP's own source-set precedence, so a flavor-specific icon
wins over the one in main — and followed: adaptive-icon to foreground, adaptive-icon to monochrome.
Background is read but not modified.

**Nothing is assumed for an attribute nobody declared.** An undeclared attribute names no icon: the
plugin modifies the icons an app has and invents none. For `roundIcon` that follows the platform —
Android populates `ApplicationInfo.roundIconRes` from `android:roundIcon` alone, so a
`mipmap/ic_launcher_round` that no manifest points at is artwork no launcher ever loads, and bannering it
would write output nothing displays. A declaration that *is* present must resolve, and one that is not a
resource reference fails rather than being quietly ignored. A variant with no `android:icon` at all has
nothing to banner and the task does nothing.

Every qualified copy of a resolved drawable is rewritten and re-emitted under its original
qualifier, so an icon that varies by density or API level stays consistent — and no qualifier is ever
added. `anydpi` stays `anydpi`, a density the project does not ship stays unshipped, and the only output
whose name differs from its source is a WebP re-encoded as PNG (same resource, same qualifiers) and the
reserved monochrome copy described under Rasterized icons.

If the resolved icon is a plain `<vector>` rather than an `<adaptive-icon>`, it is bannered directly
— the same rewrite without the layer traversal and without a monochrome variant. A bitmap is bannered
in its pixels, whether it stands alone or backs an adaptive layer; see Rasterized icons. Only an icon
with nothing bannerable anywhere in its graph is a failure.

Standalone banners not attached to a launcher icon are out of scope.

#### Known follow-up: merger directives are not interpreted

Reading the source manifests rather than the merged one is what keeps discovery configuration-cache
safe and free of a dependency on the merge task, and source-set precedence gets the same answer the
merger would for an ordinary declaration — including `tools:replace`, which works by accident, since
the manifest that replaces is also the higher-priority one.

`tools:remove` is the case that does not survive. Found by building the demo app's `plainVector`
flavor, which has no round icon: the flavor manifest removed `android:roundIcon`, the plugin read the
declaration in `main` anyway, and the build failed with `@mipmap/ic_launcher_round was not found` for a
name the app does not in fact declare. The demo now declares its icons per flavor instead of removing
them, which is the better arrangement for a project whose flavors differ in icon anyway, and the
limitation is documented rather than worked around. Interpreting the directives would mean either
parsing `tools:` attributes — a partial reimplementation of the merger, since precedence, node merges
and `tools:node` all interact — or consuming the merged manifest and accepting the task dependency.
Neither is worth it for an attribute a project rarely removes.

### AGP integration

Built against the AGP 9 variant API only, with no compatibility shims for AGP 8 — determined by
inspecting the AGP 9.3.1 API jar rather than from documentation.

DSL blocks are registered through AGP's own extension mechanism, whose builder offers exactly three
slots: project, build type, and product flavor. Notably **there is no `defaultConfig` slot**, so the
project-level block inside `android { }` is the defaults slot. AGP hands the plugin the project
extension, the build type extension, and the ordered list of flavor extensions for each variant,
which are the exact inputs to the merge.

Generated resources are contributed through the variant sources API. A trap found during API
inspection: the variant's resource sources expose both an "all" and a "static" view, and "all"
includes generated directories — including the one the plugin itself registers. The plugin reads the
**static** view. Using "all" creates a task dependency cycle.

Applied only to the application plugin. Library and dynamic-feature modules are a no-op.

### DSL and merge semantics

Shape agreed during design:

```kotlin
android {
  iconBanner {                    // project-level defaults
    color = "#FF0000"
    textColor = "#FFFFFF"
    corner = TOP_LEFT             // shipped as a bare accessor, `corner = topLeft`, so no import
    height = 20                   // superseded: now maxTextSize + lineHeight, see Geometry
    font = "Roboto Mono"
    weight = 700
  }
  productFlavors {
    dev  { iconBanner { text = "DEV" } }
    prod { }                                       // no banner
  }
  buildTypes {
    debug { iconBanner { text = "DEBUG" } }        // beats flavor text
  }
}
```

Precedence is build type, then flavors in dimension order (first dimension wins), then project —
identical to AGP's own rule for every other setting.

`text` uses an assignment-tracking holder accepting a `String`, a `Provider<String>`, or `null`,
backed internally by a property plus a "was assigned" flag. This exists because Gradle offers no way
to ask "was this property assigned?" without evaluating it, and evaluating would defeat lazy
providers. The flag gives the intended semantics precisely:

| assignment | banner? | when is the value read |
| --- | --- | --- |
| not mentioned | no | never |
| `"DEV"` | yes | configuration |
| `""` | yes, empty ribbon | configuration |
| a provider | yes | execution |
| `null` | no — overrides inherited | never |

Because assignment is observable at configuration time, enablement is decided there, and no
`enabled` flag is needed: `text = null` is the opt-out. Every other property is an ordinary lazy
property merged by `orElse` chaining; none of them ever needs early evaluation.

`weight: Int` and `italic: Boolean` are used in place of a `bold` boolean. AWT will not synthesize a
convincing bold from a regular face, but the font service will serve the genuine weight, so the
weight axis is passed through to the request instead.

#### Extended: named banners

One banner per variant was never argued for; it was simply all there was. The case that breaks it is
two markers of *different* kinds — the environment in one corner and a git SHA in the other, stories
1 and 22 wanted at the same time. Concatenating them into one `text` does not work: the ribbon's
length is fixed by the mask, so `"STAGING 1a2b3"` is drawn below the legibility floor.

`banner("sha") { … }` declares one, inside the same `iconBanner { }` block, in all three slots. The
container is a `NamedDomainObjectContainer<IconBannerSpec>` and `banner` is `maybeCreate` rather than
`create`: re-declaring a name in a higher-precedence block is the *point* — it is how a flavor refines
what the project block declared — not a collision to report.

**Two tiers.** A property written directly in `iconBanner { }` is a default for every banner in that
block's scope; the same property on a named banner overrides it. Each banner therefore resolves
against its own declarations down the precedence chain first, then the block-level ones behind them.
The build-type/flavor/project order is unchanged at both tiers, so this adds one rule rather than a
second scoping mechanism.

**`text` is the exception, and does not fall through.** The block's own properties are not only
defaults; they are also a banner — the one named `main`, which is bit for bit the banner the plugin
had before this change, so every existing build script keeps its meaning. A block-level `text` is
`main`'s marker. Letting it fall through would hand `banner("sha")` the same word under a second name
and stamp it on the icon twice, which is never what was meant. `main` is reserved: `banner("main")`
fails, pointing at the block's own properties.

A named banner nobody ever gives text to is silently no banner. Deliberate rather than tolerated:
declaring a banner's style at project level and its text on one flavor alone is the normal shape, and
it is what keeps an unbannered flavor unbannered.

**Removal is `text = null`.** `banner("sha").remove()` is sugar for exactly that and nothing more. A
flavor cannot literally remove an element from the project block's container, because AGP gives every
DSL slot its own extension instance and therefore its own container: the flavor's container never held
that banner and has nothing to take out of it. Refusing to inherit the text is already the mechanism
that decides whether a banner exists at all, so it is the mechanism removal uses.

The assignment-state table above still holds, once per banner.

**Declaration order is recorded, not read back off the container.** A `NamedDomainObjectContainer`
iterates alphabetically and nothing about the DSL implies that order, so each block keeps the order
names were written in. The chain is walked lowest-precedence first, so a banner the project block
introduced sits behind one a flavor added on top of it — the order a reader of the build script would
expect from the way overrides work everywhere else. `main` is pinned first: it is declared nowhere and
exists in every block, so there is no position to observe.

Task names are unchanged — one `download<Variant>IconBannerFont` and one `generate<Variant>IconBanner`
per variant, however many banners it carries. Both tasks read and rewrite the same icon resources, so
splitting them per banner would put several tasks in one generated resource directory. The font task
now emits a *directory* with one file per distinct face, deduped on the resolved values at execution
time rather than during configuration, where comparing lazy providers would mean forcing them.

#### Overlap and `z`

`z: Property<Int>`, default 0. Higher is painted later and ends up on top; ties break by declaration
order.

Overlap is the normal case rather than the pathological one, and the geometry is what says so. With
the centre line pinned at `0.72 * s`, a TOP_LEFT band at the default style covers
`x + y ∈ [0.58s, 0.86s]` and a TOP_RIGHT one covers `(s − x) + y` over the same range. Their centre
lines cross at `(0.5s, 0.22s)` whatever the thickness, and that point is `0.28s` from the icon's
centre against a mask at `0.33s` — so the overlap is drawn rather than masked away. **Only opposite
corners are disjoint**: TOP_LEFT with BOTTOM_RIGHT, TOP_RIGHT with BOTTOM_LEFT. Adjacent corners
cross, and the same corner obviously does.

Two banners in one corner **warn**; they do not fail. `z` exists precisely so the user can say which
of two overlapping banners is the readable one, and a marker stacked over a coloured band is a
legitimate thing to want — failing would take that away. It is still worth saying out loud, because
the usual cause is a flavor adding a banner without noticing the project block already put one there,
and the result is one marker quietly buried. Adjacent corners are deliberately not warned about: two
different corners are a layout somebody chose, two banners in one corner are a layout nobody chose.

Sorting by `z` happens in the generate task, at execution time, because `z` is a lazy property like
every other. A plain stable sort over the declaration-ordered list is the whole implementation —
`sortedBy` leaves equal elements where they were, so declaration order *is* the tie-break without an
index having to say so.

#### Rejected: keying banners by corner

`iconBanner { topLeft { … } }`, at most four per icon. It reads well and makes two banners in one
corner unrepresentable rather than merely warned about.

Rejected because it spends `corner`. A banner's corner would be its identity, so `corner` could no
longer be a per-variant overridable property, and moving the SHA out of the way on one flavor would
mean declaring a *different* banner there instead of overriding one line. Overlap is worth a warning;
it is not worth losing an override to prevent.

#### Rejected: a project-level container with AGP variant selectors

Declare every banner once in `android { iconBanner { } }` and attach each to variants through
something like `components.selector()`.

Rejected for the reason the merge follows AGP's precedence in the first place: it abandons the
build-type/flavor inheritance model the rest of the block already uses, and it introduces a second
scoping mechanism — for banners specifically — that a user would have to learn *alongside* AGP's own
and keep straight from it.

#### Known follow-up: edge banners

Half done. The offset half shipped as `position` — see "the fixed centre line became `position`"
below for the scale and for why it is anchored where it is. Banners are still pinned to the four
corners; the remaining extension is edge positions (top, bottom, left, right).

That half is cheap now, and deliberately so: `position` is a distance from the icon's centre, which
is the one measurement a corner and an edge share, so an edge banner is a new entry in the corner
table and a rotation, not a second sizing model. `Ribbon.perpendicularFromIconCentre` and the quad
tables are the only places that know a corner from an edge.

One thing to decide when it happens, and it is less obvious than the four sides sound. At the default
position an edge band lands 27.7–37.5 on a 108 canvas against a visible mask of 18–90 — a belt across
the upper third rather than a strip hugging the top. Hugging the rim is not available: the text budget
is zero past `0.306 * s` from the centre and the edge itself is at `0.5 * s`. So edge banners either
read differently from corner ones or they want a different default, which is a choice about what an
edge banner *is* rather than about geometry.

This note used to date the shared-corner warning, on the reasoning that two banners in one corner at
different offsets would no longer overlap. That turned out to be wrong and the warning is unchanged:
both bands run parallel to the same diagonal, so they separate only once the gap between their centre
lines exceeds half of each band's thickness. That is a stacking arrangement, which is not a thing this
plugin sets out to support.

### Font acquisition

Always downloaded; nothing is bundled in the plugin jar.

Resolution is a two-step fetch validated by prototype: query the Google Fonts CSS endpoint for the
family, weight and italic axes with a non-modern user agent, which returns a direct TrueType URL
rather than woff2. This avoids needing an API key and avoids pulling in a brotli/woff2 decoder
entirely. The returned URL is then fetched.

Both steps happen at execution time so configuration caching is unaffected. The download task's
inputs are family, weight and italic, so incremental builds are up-to-date and make no network
calls. Results are mirrored into a shared cache under the Gradle user home keyed by the resolved
font URL, so the cost is paid once per machine rather than once per project, and survives `clean`.
Offline builds serve from that cache or fail with the URL they needed.

### Text outlines

Glyph outlines come from the JDK's own font support — load the TrueType file, lay out a glyph
vector, take its outline, and walk the path iterator. Validated by prototype, which confirmed the
two properties that matter: real quadratic and cubic segments are preserved rather than flattened
into line soup, and the coordinate convention is baseline-at-zero with y increasing downward, which
is exactly the VectorDrawable convention. No transformation or axis flip is needed, and no
third-party font library is required.

Note for the monochrome path: the outline's natural winding rule is non-zero, but the combined
ribbon-plus-text path is emitted with even-odd so glyphs become holes. Counters inside letters
remain correct under even-odd; fonts with self-overlapping contours are a known and accepted
limitation.

### Geometry

`height` is a percentage of the icon's edge length, normalized against whatever viewport the target
vector declares, so the same value produces the same visual result on a 108-unit or a 24-unit icon.

The ribbon's **corner-side** edge is pinned at 60% along the corner diagonal, so a taller band grows
towards the middle of the icon rather than out towards the corner. Text is centered on the band and
scaled to fit the smaller of the band height less padding and the available chord length less
padding. There is no font-size knob: this is a debug marker and a build tool has no live preview, so
tuning two sliders blind was rejected in favour of one knob plus auto-fit.

Both of those numbers are set against the **adaptive-icon mask**, not against the full 108 square,
and that distinction turned out to matter more than expected. A launcher masks the icon to a circle;
the browser generator's preview drew the whole square, so nothing in it revealed that its defaults
put the ribbon partly outside the visible area. On a real device the band's ends were sheared off
and the first and last glyphs of the text with them.

So: the corner-side edge sits inside the 66dp safe zone at the default band width, and the text
length budget is the chord across that safe zone rather than across the square. Long text shrinks
instead of spilling. A banner nobody can read defeats the point of the plugin.

Text is used verbatim. The browser generator force-uppercased; the plugin does not.

Superseded before the first release, and the sizing model above is inverted rather than adjusted.
`height` is gone; `maxTextSize` (cap height as a percentage of the edge) and `lineHeight` (band
width as a multiple of the text size) replace it. Story 30's "one knob" is therefore two — but they
are independent, which one knob plus auto-fit never was.

What was wrong is visible in the numbers. Measured on the sample app — `height = 40`, Black Ops
One, `"STAGING"`, a 108 viewport — the band came out 43.2 units wide with a text budget of 27.65
units and a fitted cap height of **6.03**. The text used 22% of the vertical room it was given.
Anything longer than about three characters is length-bound, never height-bound, so the band's
thickness was mostly empty space, and worse: the text clearance was 0.18 of the *band width* at
each end as well as above and below, so a thick band spent 27% of the chord it had on padding the
text away from the ends. Deriving the band from the text instead — band width as the text size
times the line height, padding as the leftover, end clearance as a fraction of the text's own cap
height — makes the band hug the text and removes both problems. The demo case is now a 9.78-unit
band around a 6.52 cap height: thinner band, larger text.

The part of the original design that made this necessary rather than merely nicer is the anchoring.
Pinning the ribbon's corner-side edge and letting the band grow inwards meant **band thickness was
implicitly buying ribbon position**, and position is what sets the text's length budget: further
in, the chord across the safe zone is longer. That was invisible while thickness was a user
setting. It is fatal once thickness is derived from the text, because the dependency closes into a
loop that runs the wrong way — long text shrinks, the band thins, the ribbon slides back towards
the corner, the chord there is shorter, the text shrinks again. Solving that equilibrium for
`"STAGING"` gives a 4.45-unit cap height, under 3dp on a launcher, worse than the model it was
supposed to improve on.

The fix is to stop the band width from carrying position: the band is now centred on a **fixed**
line at 0.72 of the shorter edge from the corner, and grows symmetrically about it. 0.72 is set
against the mask, but by a different criterion than the old 0.60 — worth recording, because the
obvious one does not survive. Pinning the corner-side edge on the safe-zone rim, which is how 0.60
was justified, now lands the centre line at 0.67, and the safe-zone chord there is short enough that
`"STAGING"` solves to 3.6dp and trips the legibility floor. The safe zone is therefore a *lower*
bound on how far in the line has to be, not the choice itself. The upper bound is the icon's middle:
at 0.72 the band's inner edge sits `0.129 * s` from the centre and its corner-side edge `0.267 * s`,
comfortably inside the 66dp safe zone, and `"STAGING"` solves to 6.5 units — 4.3dp. Further in keeps
buying text room, but at 0.80 the inner edge is `0.073 * s` from the centre and the ribbon reads as
a stripe across the artwork. That trade is why the constant is argued for in the code rather than
tuned by eye.

Two consequences worth recording. The circularity is gone entirely: with the centre line fixed, the
length budget is independent of the text, so the size is one division and there is no solve to
iterate. And there is no longer any knob that helps text that is too long — the length it competes
for is fixed geometry — so the legibility warning stops suggesting one and says to shorten the text
instead. `SAFE_ZONE_FRACTION` and the safe-zone chord are untouched; they were the fix for real
on-device clipping and the geometry still keeps the text inside the safe zone.

#### Superseded again: the band was √2 too thin

`lineHeight` was documented and computed as a thickness measured *across* the ribbon, and then written
straight into the quad's offsets *along each axis*. For a top-left band the long edges lie on
`x + y = b` and `x + y = b - w`, whose perpendicular separation is `w / √2`, so every band was drawn
at 71% of the thickness asked for and `lineHeight` behaved as `lineHeight / √2`. At the default 1.5
the real thickness was 1.06 cap heights: about 3% of the glyph height clear at each long edge, which
reads as text touching the ribbon, and which is the complaint that found it. The "9.78-unit band
around a 6.52 cap height" recorded above is the same mistake in this document — that was the axis
measurement of a band 6.92 thick.

Measured on the sample app before the fix, from the generated `pathData` projected onto the band's
normal: a 10.14 band around a 6.52 cap height, the glyphs filling 64% of it with 1.81 clear on each
side. Afterwards the band is exactly `capHeight * lineHeight`, whatever `lineHeight` is set to.

The same mistake is in the edge offsets quoted for 0.72 above, and they are worth correcting here
rather than leaving to be re-derived: at the default style the inner edge sits `0.100 * s` from the
icon's centre and the corner-side edge `0.295 * s` — not `0.129` and `0.267` — against a rim at
`0.306 * s`. At 0.80 the inner edge is `0.044 * s`, not `0.073`. The band is thicker than that
paragraph thought, so it reaches closer to the rim on one side and closer to the centre on the other;
the conclusion it was drawn for survives, because at the default the corner-side edge is still inside
the safe zone and the centre of the artwork is still uncovered. `Ribbon.DEFAULT_POSITION_PERCENT`
(where this constant ended up, restated on the `position` scale as 65) carries
the corrected figures.

The fix is naming, not a correction factor sprinkled at the call site. Axis-measured lengths carry an
`Axis` suffix — `centreLineAxis`, `innerEdgeAxis`, `cornerSideEdgeAxis`, `bandWidthAxis` — everything
else is a true distance, and `Ribbon.perpendicularFromIconCentre` is the only place the two meet. The
ambiguity *was* the bug: the old `bandWidth` was right as an axis offset and wrong as the thickness
its own documentation claimed, and neither a golden file nor a coordinate assertion could tell those
apart. The tests now project the quad onto the band's normal and assert the separation of the two long
edges, in all four corners and on a non-square viewport, which is the one measurement the old suite
never took.

Nothing about the text moved, and that is checkable rather than hoped for: the fitted size never
depended on the band, and the pivot is the centre of the quad, which sits on the pinned centre line at
any thickness. Every golden file's diff is therefore the ribbon quad alone — the band widening
symmetrically about an unchanged centre line — with the glyph outlines byte-identical.

#### Superseded again: `lineHeight` must not constrain `maxTextSize`

The two knobs were validated as a pair, `maxTextSize` capped at `30 / lineHeight`, so a `lineHeight`
of 2.4 rejected the documented default of 13 with a message naming `1..12` as the range and 13 as the
default in the same breath. A DSL whose default is invalid at some setting of another property is
incoherent, and the premise behind the coupling does not hold: the bound existed to keep the *band*
inside the mask's safe zone.

The band does not have to be inside it. The centre line is pinned, so thickness grows symmetrically
about it — the corner-side edge moves towards the corner, where a launcher's mask declines to draw it,
and the inner edge moves towards the middle of the icon, which is taste. Neither costs the text
anything, because the text is fitted against the chord across the safe zone and that chord does not
depend on the thickness. **Only the text has to stay inside the safe zone.**

So each value is now bounded on its own. `maxTextSize` stops at 21: the glyphs reach half their cap
height either side of the centre line, which sits `0.198 * s` from the icon's centre, and the rim is at
`0.306 * s`, so `2 * (0.306 - 0.198) = 21.5%` of the edge is where the mask starts cutting into the
glyphs themselves. `lineHeight` keeps `1.0..3.0` as a sanity range — 1.0 is now genuinely the point
where the band is the height of the glyphs, and the range still has to catch a 0 or negative value,
which is a degenerate or inverted band. `BannerTextSize` is renamed `BannerGeometryBounds`, since
there is no longer a text size being checked against anything but the mask.

Defaults re-tuned rather than preserved. 13 and 1.5 are the same numbers, but 1.5 is now argued from
the mask rather than from continuity with the band-width knob it replaced — that continuity argument
was itself computed in axis units, so it was matching one wrong number to another. Honestly measured
it leaves a quarter of the cap height clear at each edge, and at `maxTextSize = 13` the band's
corner-side edge still lands inside the safe zone (`0.295 * s` against `0.306 * s`), so even a short
marker's band is drawn in full under every mask; looser than that loses the corner of the band and
pushes the inner edge across the artwork. Rendered side by side at 1.0, 1.25, 1.5, 1.75 and 2.0, that
is also where it stops looking crowded and has not yet started looking heavy. The sample app drops
from `lineHeight = 2.2` to 1.8, and not because 2.2 was equivalent to it: 2.2 drew a 10.1 band under
the old arithmetic and asks for 14.3 under the fixed one, so keeping it would have made the preview
heavier than anything the defaults produce. 1.8 asks for the 11.7 the demo wants.

#### Superseded again: the fixed centre line became `position`

The pinned line was the right call and the wrong permanence. What it settles — text room against how
much of the icon the band covers — is a judgement about *this* banner's text, and a plugin that lets
one icon carry several of them has no single right answer to it. A five-character sha wants a small
tab in the corner; `STAGING` wants the broad stripe the default gives it. So the line stayed
independent of the band's thickness, which is the property everything rests on, and stopped being a
constant.

**The scale is anchored on the safe-zone radius**, not on the canvas: `position` is `d / safeRadius`
as a percentage, where `d` is the perpendicular distance from the icon's centre to the centre line. 0
is the icon's centre, 100 the distance at which the text budget is exactly zero. Three things follow,
and the third is why this anchor beat the alternatives. The endpoint is real rather than a convention.
The whole model collapses to `budget = 2r · √(1 − position²)`. And because it is a distance from the
centre rather than an inset from an edge, one value means the same text room at a corner as on an
edge — which the planned top/bottom/left/right banners need, since a corner is `√2` further from the
centre than an edge is and any canvas-anchored spelling would have meant two different things there.

Anchoring at the mask cutoff would have put today's value at 59 and at the literal canvas corner at
28; the safe radius puts it at **65**, and `DEFAULT_POSITION_PERCENT = 65` is the old `0.72` restated,
to within 0.1 units on a 108 canvas. Every golden file moved by that 0.1 and nothing else — the
diffs are the ribbon quad and the glyph outlines shifted together, with sizes unchanged.

Three spellings were considered and two rejected:

- **A signed `offset` from the default**, in percent of the shorter edge. Reads the way the knob is
  described in use — "push it out a bit" — but it needs a sign convention, hides the default, and
  makes the range look arbitrary. `20..95` reads as a scale; `−8..+8` reads as a fudge factor.
- **An offset in band thicknesses**, so that `offset = 1` lands flush against a neighbour. Rejected
  outright: position would derive from thickness, which derives from the fitted text size, which
  derives from the length budget, which derives from position. That is exactly the spiral the pinned
  line was introduced to break, three subsections above, and solving it for `"STAGING"` gave a 4.45
  cap height against 6.5 pinned. It also only serves stacking two banners on one edge, which this
  plugin does not set out to support.
- **Auto-fitting** the position to the text — solve for where the text just reaches `maxTextSize`.
  Closed form, no loop, and still wrong: 13% cap height needs a chord only about three characters ever
  reach, so every banner would clamp to the innermost bound and come out a stripe through the middle.
  For genuinely short text both directions are defensible and the plugin cannot infer which was meant.

**The trade is asymmetric, and the docs say so.** Going out costs text size quickly — a third of the
ribbon's length by 85 — while coming in buys very little, because the chord is flat near the centre.
Below about 40 the band's inner edge crosses the middle of the artwork. So this is fine-tuning, and
how much of it is available depends entirely on the text: five characters reach about 90 before the
4dp floor, `STAGING` is already at 4.3dp at the default and has nowhere to go. `MIN_POSITION` is 20
on taste and to catch a scale used upside down; `MAX_POSITION` is 95 on geometry, since the budget
reaches zero at 100.

Two knock-on decisions. The legibility warning now names `position` as the cause, but **only when the
banner was pushed past the default** — suggesting it to someone on 65 would send them towards a
setting that buys a few percent and costs them the centre of their icon. And `maxTextSize` keeps its
static `1..21` ceiling even though the limit it approximates now moves: the true bound is
`2 * (safeRadius − d)`, which falls to 6% at position 90, so validating against it would make the
documented default of 13 illegal past about 78 and turn one knob into a two-knob ritual. `Ribbon`
clamps the fitted size to that bound instead — a third term beside "as asked for" and "as long as it
fits" — which is the same shrink-rather-than-fail behaviour over-long text has always had. 21 is
exactly the value at position 65, so nothing a build script could previously ask for is clamped, and
a build that never touches `position` sees none of this.

### Failure policy

Deliberately quiet, because this is a debug convenience rather than a correctness feature:

- Nothing to banner at all — no vector and no usable bitmap — on a variant that asked for one:
  **fail**, naming the resource and the reason. A silently unmarked build is the failure mode the whole
  plugin exists to prevent.
- A bitmap no reader can decode, and a nine-patch — **skipped with a warning** naming the file and its
  own reason. The old policy was to skip silently, on the grounds that rasterization was a shelved
  feature; the shelving is gone, and so is the silence.

  "Cannot decode" includes a reader that *throws* rather than declining, which is why the decode catches
  every `Exception` and not only `IOException`. The breadth is deliberate: what a reader throws on
  malformed input is its own choice — a frame parser fed a truncated file throws whatever its arithmetic
  produced — and the reader in question may have been resolved from the consuming project's own
  configuration, so it is code this plugin neither ships nor controls. One corrupt icon file must cost
  one warning and not the build. `Error` still propagates: an `OutOfMemoryError` is not a verdict on the
  file. The one place where a decoding problem does still fail the build is recorded under Rasterized
  icons — "an unresolvable reader throws where an undecodable file only warns".
- A resource that has files but produced no output at all — **fail**, quoting each file's own reason.
  `GenerationResult.Failure` carries no warnings, so a generic sentence would leave the user with
  nothing to act on. A resource with no files at all fails as it always did.
- No monochrome layer present — skipped silently.

The rule underneath all of them is unchanged and worth restating: a variant that asked for a marking
must never silently get none.

### Correctness details

Small decisions, recorded because each one is a plausible way to ship something subtly broken.

- **All number formatting pins the root locale.** On a JVM with a comma decimal separator — the
  author's default — ordinary float formatting emits `23,10` into `pathData` and silently corrupts
  every generated icon. This is the most likely works-on-my-machine failure in the whole design.
- **Generated XML is byte-deterministic** and the generate task is cacheable with relative path
  sensitivity. Golden tests and the build cache both depend on it.
- **Font cache writes are atomic** — write to a temporary file, then move. Two concurrent builds
  fetching the same font into the shared cache would otherwise interleave into a truncated file.
- **Text containing glyphs the font does not provide fails the build**, naming the characters. A row
  of missing-glyph boxes on the icon is worse than a build error, and it follows the rule already
  established: fail when the marking would be wrong rather than ship it wrong.
- **Nested components are skipped.** The androidTest APK gets no banner.
- **Colour values must be hex literals.** The four accepted forms are `#RGB`, `#ARGB`, `#RRGGBB` and
  `#AARRGGBB`, expanded the way **aapt2** expands them — each nibble doubled, so `#ABC` is `#FFAABBCC`.
  That rule matters because for a vector the very same string is handed to the resource compiler, and the
  two icon forms have to agree on what a colour means. Note that `android.graphics.Color.parseColor`
  rejects the short forms outright, so it is not the rule to copy here.

  Theme attributes (`?attr/…`) were originally allowed to pass through, on the reasoning that Android
  accepts them in a `fillColor`. It does — but a launcher inflates the icon from the APK's resources with
  no theme attached, so the attribute has nothing to resolve against and the likely outcome is no icon
  rather than a fallback colour. They are rejected with their own message, since that failure is the
  worst of the lot: a build error is better than an icon that will not load.
- **A `fun interface` absorbs a new parameter silently, and `ImageCodecs` is one.** When
  `ensureReadersAvailable` gained its `resourcePath` parameter, every existing `ImageCodecs { }` lambda
  went on compiling without a word — a lambda with no parameter list satisfies a one-parameter SAM
  through the implicit `it`, so nothing at any call site says "update me". During that change one such
  lambda threw `AbstractMethodError`, bound to the method signature that no longer existed; from a clean
  build it does not reproduce, so incremental compilation is the likely culprit rather than the
  conversion itself. Either way the call sites now spell `{ _ -> }` out, which is what makes a future
  signature change a compile error instead of a silent adaptation. `{ -> }` — an explicit empty
  parameter list — *is* rejected outright, so it is only the bare `{ }` that hides.
- **Generated monochrome drawables use a reserved name suffix**, and the plugin fails rather than
  clobbering a resource that already holds that name.
- **Non-square foreground viewports** normalize against the smaller dimension.
- Ordering against *other* plugins that generate the same resource is undefined; last registered
  wins. Not designed for.

### Repository layout

The plugin is its own Gradle build, included from the root build's plugin management so the sample
app applies it by id and picks up live edits. Publishing configuration is present from the start so
the released artifact is never an afterthought. The existing app module doubles as the manual visual
test bed.

#### The demo's flavors are one per kind of icon

`:app` carries a single `icon` flavor dimension with one flavor per launcher icon the plugin can be
handed: `adaptiveVector` (adaptive, vector foreground, `<monochrome>` reusing that foreground),
`adaptiveRaster` (adaptive, PNG foreground, `<monochrome>` with a PNG of its own), `legacyRaster`
(WebP mipmaps under names of their own, no adaptive icon at all), `plainVector` (one unmasked
`<vector>`, no round variant) and `prod` (`adaptiveVector`'s icons with no banner configured). One
dimension rather than crossing icon kind with an environment: the cross is twenty variants for no
extra coverage, and the four bannered flavors already carry identical text and style so that the icon
is the only variable. Each has its own `applicationIdSuffix` and label, because the check this exists
for is looking at all five on one launcher at once.

Two consequences of AGP, both load-bearing:

- **The icons live in the flavors, not in `main`.** Resource merging can override a file but never
  remove one, so a flavor sharing `main`'s icon set can never be the project that has no
  `mipmap-anydpi-v26/ic_launcher.xml` — which is exactly the case `legacyRaster` exists to cover.
  `prod` reads `adaptiveVector`'s directory through `res.srcDir` rather than keeping a second copy of
  fourteen files for a difference to hide in.
- **`android:icon` is declared per flavor too**, for the reason under Icon discovery: `tools:remove`
  is invisible to the plugin, so a `roundIcon` declared in `main` is a name every flavor has to
  resolve. With nothing declared in `main` there are no merger directives anywhere in the demo.

The raster artwork is derived, not drawn: `scripts/demo-icons.sh` renders `adaptiveRaster`'s layers
from `adaptiveVector`'s own vector at five densities and copies the WebP mipmaps under
`legacyRaster`'s names, so the flavors really do differ only in the kind of file. The split of formats
is deliberate — `adaptiveRaster` ships PNG, which the JDK reads, and `legacyRaster` ships WebP, which
needs the resolved reader, while `plainVector` has no bitmap at all and so is the flavor that
demonstrates the reader is never fetched.

### Delivery mechanism: same-name override

Settled by a throwaway spike against this project rather than by reasoning. The generated resource
is emitted under the **same name** as the original; the manifest is not touched. Name, not file name —
a bannered webp comes out as a PNG and still overrides, which a later spike confirmed under Rasterized
icons.

What the spike established:

- A generated resource directory registered through the variant sources API wins over a same-named
  resource in the main source set. The resource merger orders the plugin's generated set last, so it
  takes precedence by ordinary source-set rules. The build succeeds with no duplicate-resource
  error, and the original file is never even handed to the resource compiler.
- It also outranks build-type and flavor source sets, not just main.
- Rebuilds report the generate task up-to-date, and the configuration cache entry is reused.
- The merged-manifest fallback works too, but carries a defect the same-name route does not: a newly
  named mipmap existing only in `anydpi-v26` has no matching configuration on API 24 and 25, so that
  route would additionally have to synthesise legacy density entries. Same-name inherits them.

Two constraints follow, and both are load-bearing:

1. **Replacement is whole-file, not element-wise.** Anything the plugin does not re-emit is lost. The
   spike lost the original `<monochrome>` element by omitting it. Every rewritten file must carry
   over all of its original content — background, monochrome, and the round icon's own adaptive-icon
   XML.
2. **The source must be resolved at the correct precedence.** Since the generated set outranks flavor
   and build-type source sets, a variant-specific icon in a flavor source set would be overridden.
   The plugin must therefore resolve each resource through the same precedence AGP would apply and
   transform the *winning* file, not the one in main.

Also noted: the override is entirely silent — no merger message, no lint warning. Good for the
plugin, unhelpful for a user wondering why an edit to their foreground vector had no visible effect.
Worth an info-level log naming each resource the plugin displaced.

One accepted behavioral consequence: same-name override changes every use of that drawable, not only
the launcher icon — a splash screen icon pointing at the same foreground would also gain the banner.
For a debug marker this is arguably desirable.

Minor API note from the spike: the variant's resource sources are nullable, absent when the
`android.buildFeatures.androidResources` feature is off. The plugin must handle that rather than
assume.

## Testing Decisions

A good test here asserts observable output, not internal structure. For the generator that means the
content of the resource files a given configuration produces; for the Gradle layer it means which
tasks ran and what the build emitted. Tests must not reach the network, and must not assert on
intermediate objects, helper class names, or the order in which the plugin happens to visit files.

### Primary seam: the generator

A pure function from a request — resolved configuration, the source icon XML as text, and a font
file — to a map of relative resource path to file content. No Gradle, no AGP, no network, no
temporary directories. This is where the overwhelming majority of the logic lives and where nearly
all tests go.

Covered by golden-file comparison, using a font file checked into test resources so the suite is
hermetic:

- Each of the four corners.
- Colored foreground output and monochrome output for the same input.
- Short text versus long text, demonstrating the auto-fit and centering behavior.
- Empty text producing a ribbon with no glyph path.
- Colors with alpha.
- A source vector with a viewport other than the default, proving height normalization.
- A source vector whose root already contains groups and clip-paths, proving the monochrome wrap
  does not disturb existing structure.
- An adaptive icon whose foreground and monochrome share one drawable, proving the redirect.
- An adaptive icon with no monochrome layer.
- An adaptive icon carrying elements the plugin does not modify, asserting they survive into the
  output — the spike showed replacement is whole-file, so silent content loss is a live risk.
- A foreground that is not a vector, asserting the failure and the content of its message.
- An icon that is a plain vector rather than an adaptive icon.
- Text containing a glyph the font lacks, asserting the failure names the character.
- The whole suite run under a comma-decimal-separator default locale, producing identical golden
  output.

Golden files are readable XML and reviewed as part of any geometry change; a diff in review is the
signal that geometry moved.

### Raster cases: pixel assertions rather than golden files

The bitmap cases assert pixels — a colour at a point derived from the default style's band, or a count
of ribbon-coloured pixels where the band is clipped to artwork — rather than comparing against a
checked-in PNG. Two reasons, and the second is the whole point of the goldens. A golden bitmap would be
hostage to the JDK's PNG encoder, so an upgrade of the JDK would read as a geometry regression. And a
binary diff is unreviewable, whereas a golden file earns its keep precisely because its diff in review
is the signal that geometry moved. The geometry is already pinned by the ribbon tests and the vector
goldens; what is left for these is the compositing.

- A standalone icon's band clipped to its own silhouette, and an adaptive foreground's band surviving
  a transparent surround — asserted in both directions, because the caller states which it has and the
  pixels cannot say.
- Every band cleared before any band is filled, with two banners on one monochrome bitmap.
- The themed bitmap's alpha matching the themed vector's fill, so one percentage cannot mean two
  opacities.
- The same relative placement at 48px and at 192px, which is what "no raster sizing knob" amounts to.
- Greyscale and indexed sources decoding as ARGB, and a bitmap without an alpha channel refused.
- A nine-patch and an undecodable bitmap skipped with a warning while a vector beside them is still
  bannered; a resource whose every file was skipped failing and quoting each reason.
- The same bitmap encoding to identical bytes across runs, which `@CacheableTask` depends on.
- The extra readers asked for once and only once a bitmap the JDK could not decode turns up, named with
  the file that failed; a bitmap the JDK reads, and a vector-only icon, never asking at all.
- Garbage bytes, a truncated image and a claimed signature over rubbish all decoding to null rather
  than throwing. The pinned WebP reader turned out to be careful — every truncation and every byte
  mutation of the checked-in webp comes back as an `IIOException` — so a reader of the suite's own,
  registered into `IIORegistry` for one test, is what pins the `RuntimeException` case and the
  `Error`-still-propagates case.

**One real WebP is checked in** — the project's own hdpi launcher icon, VP8 lossy with alpha in a
separate `ALPH` chunk — as the decode fixture. Everything else builds its bitmaps in memory, and a
generator fixture named `.webp` may well hold PNG bytes: what makes a file a bitmap comes from the
lookup rather than from its name, and the extension only matters to the *output* name, which is why
the fixtures wear one at all.

**One TestKit case resolves the reader from a repository**, since the JDK cannot decode WebP at any
seam. That is consistent with a suite whose every TestKit case already resolves AGP itself, so it does
not change what these tests are; the pure-seam WebP case stays hermetic because the reader is a
`testImplementation` dependency and so already on the test JVM's classpath. Its complement — a
vector-only build whose reader coordinates point at a module that does not exist — is worth a whole
real build of its own, because what it guards is the configuration cache's store-time resolution, and
no unit test can see that moment.

The trap in asserting a whole result at once: `GeneratedFile.Binary` is deliberately not a data class,
so an `assertEquals` over two output maps degrades to identity comparison the moment a case emits one,
and then keeps passing. The comparison helper spells out a byte comparison per path instead.

### Secondary seam: the Gradle build

TestKit builds against a fixture project, covering only what the pure seam cannot:

- Text on a flavor banners that flavor's variants and leaves the others alone.
- A flavor-specific launcher icon in a flavor source set is the one that gets bannered, not the one
  in main.
- Build-type text overrides flavor text.
- `text = null` on a flavor overrides an inherited project-level value.
- A provider-valued text does not cause the provider to be evaluated during configuration.
- A second build reports the generate task up-to-date.
- The configuration cache entry is reused on a second build.
- The font download task does not re-run, and no request is made, on an incremental build.
- A PNG launcher icon is bannered under its own qualifier folder and the task is still up-to-date on a
  second build, with the configuration cache entry reused — the untracked reader classpath is exactly
  the sort of thing that usually breaks both.
- A WebP launcher icon is bannered through a reader resolved at execution time, and no webp is
  re-emitted beside the PNG.
- A vector-only icon builds even when the reader configuration cannot resolve anything at all.

Kept deliberately small: each case costs a real Gradle build.

### Prior art

None — this is the first plugin code in the repository, so these tests establish the pattern rather
than follow one. The font fetcher is expressed as a narrow interface so the pure seam can be
exercised with a local file, which is the only fake the suite needs.

## Out of Scope

- **Standalone banners.** Banners on drawables other than the launcher icon.
- **Manual banner placement** — arbitrary coordinates, rotation, or shapes other than the corner
  ribbon. `position` slides a banner along its own diagonal and is not a general placement knob.
- **AGP 8 and Gradle 8 support.** Deliberately deferred; the design is not structured to make
  backporting free.
- **A preview or report task.** The sample app is the visual check.
- **Bundled fonts** and any curated font list. Every font is fetched.
- **Local font files.** No property for pointing at a `.ttf` on disk.
- **Localized or per-locale banner text.**
- **Multi-line text.**
- **Play Store listing icons**, which are uploaded artifacts and not part of the build.

## Further Notes

- The browser-based generator this is modelled on remains the reference for the ribbon geometry and
  the monochrome clip-and-punch approach. Its two-slider sizing model was intentionally not carried
  over.
- The plugin embeds outlines derived from Open Font License fonts into the built application. This
  is worth an attribution note in the README even though no font binary is redistributed.

  Resolved during the pre-release review, and the conclusion is the opposite of what this note
  assumed: under the OFL no attribution is owed at all. OFL-FAQ 1.1.1 and 1.13 are explicit that
  artwork produced with a font is not Font Software and that embedding does not affect the document's
  licence, so the Reserved Font Name clause is never engaged — no derivative font exists. The real
  caveat is families under Apache-2.0 or the Ubuntu Font Licence, which have no such carve-out and
  where the derivative-work question is genuinely unsettled. Documented in the README's "Fonts and
  licensing" section and in `THIRD-PARTY.md`, which also covers the OFL test font checked into the
  test resources.
- Not every family offers every weight. A request for an unavailable weight should fail with a
  message that makes the cause obvious rather than silently substituting a different face.
- Unrelated to the feature: the root project name in the settings file contains a stray opening
  parenthesis.
