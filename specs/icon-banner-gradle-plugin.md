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
still be resolved during configuration. That is the guarantee the plugin can actually make.

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
    collapse into a solid tinted wedge the way a naive overlay would.
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
    no vector to put it on, so that I never get a silently unmarked build.
41. As an Android developer whose icon also has legacy raster mipmaps, I want the build to succeed
    quietly, so that a cosmetic limitation on very old API levels does not block me.
42. As an Android developer, I want the plugin to do nothing in library modules, so that applying it
    from a convention plugin across all modules is safe.

### Adoption

43. As an Android developer, I want to apply the plugin by id from a normal repository, so that I do
    not have to vendor it.
44. As a contributor to the plugin, I want a sample app in the repo, so that I can see the result on
    a real launcher before publishing.
45. As a contributor to the plugin, I want golden-file tests for the generated XML, so that a
    geometry regression is caught without me installing an APK.
46. As a contributor to the plugin, I want the tests to run without network, so that CI is not
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
are emitted as one combined path with `android:fillType="evenOdd"` and a fixed white fill, making
the glyphs transparent holes. This mirrors the approach already proven in the browser generator.

Where `<foreground>` and `<monochrome>` reference the same drawable — the default Android Studio
template does exactly this — one name cannot hold two different outputs. The monochrome result is
therefore written under a new name and the adaptive-icon XML is rewritten to redirect its
`<monochrome>` reference.

### Icon discovery: zero configuration

There is no DSL property naming the icon. The plugin reads `android:icon` and `android:roundIcon`
from the `<application>` element of the variant's source manifests, falling back to the conventional
`@mipmap/ic_launcher` when absent. References are then resolved against the variant's static
resource directories — at AGP's own source-set precedence, so a flavor-specific icon wins over the
one in main — and followed: adaptive-icon to foreground, adaptive-icon to monochrome. Background is
read but not modified.

Every qualified copy of a resolved drawable is rewritten and re-emitted under its original
qualifier, so an icon that varies by density or API level stays consistent.

If the resolved icon is a plain `<vector>` rather than an `<adaptive-icon>`, it is bannered directly
— the same rewrite without the layer traversal and without a monochrome variant. Only an icon with
no vector anywhere in its graph is a failure.

Standalone banners not attached to a launcher icon are out of scope.

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
    corner = TOP_LEFT
    height = 20
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

### Failure policy

Deliberately quiet, because this is a debug convenience rather than a correctness feature:

- No vector to banner at all, on a variant that asked for one — **fail**, naming the resource and
  the reason. A silently unmarked build is the failure mode the whole plugin exists to prevent.
- Legacy raster mipmaps that cannot be rewritten — **skipped silently**, no warning. Rasterization
  is a shelved feature, not a per-build problem to nag about.
- No monochrome layer present — skipped silently.

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
- **Color values pass through to the fill attribute** after a light format check, so `@color/…`
  references work alongside hex literals. Only malformed literals are rejected.

  Superseded during the pre-release review: theme attributes (`?attr/…`) were originally allowed to
  pass through too, on the reasoning that Android accepts them in a `fillColor`. It does — but a
  launcher inflates the icon from the APK's resources with no theme attached, so the attribute has
  nothing to resolve against and the likely outcome is no icon rather than a fallback colour. They are
  now rejected with a message that says why.
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

### Delivery mechanism: same-name override

Settled by a throwaway spike against this project rather than by reasoning. The generated resource
is emitted under the **same name** as the original; the manifest is not touched.

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

Kept deliberately small: each case costs a real Gradle build.

### Prior art

None — this is the first plugin code in the repository, so these tests establish the pattern rather
than follow one. The font fetcher is expressed as a narrow interface so the pure seam can be
exercised with a local file, which is the only fake the suite needs.

## Out of Scope

- **Rasterized icons.** Legacy `mipmap-*/ic_launcher.webp` and PNG icons are not bannered. Devices
  below API 26 show the unmodified icon. Explicitly shelved, and the reason the raster skip is
  silent rather than a warning.
- **Standalone banners.** Banners on drawables other than the launcher icon.
- **Multiple banners** on one icon.
- **Manual banner placement** — arbitrary position, rotation, or shapes other than the corner ribbon.
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
