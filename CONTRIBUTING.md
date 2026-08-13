# Contributing

```bash
./gradlew -p plugin build   # plugin and its tests
./gradlew :app:assembleDebug   # demo app, every icon kind
```

The plugin lives in `plugin/`, its own Gradle build, included from the root build's plugin
management. `:app` exists only as a demo and a manual visual check.

## The demo app's flavors

One flavor per kind of launcher icon, on a single `icon` dimension, so every path the plugin can take
is something you can install and look at:

| flavor           | `android:icon`                                            | what it is there for                                                  |
| ---------------- | --------------------------------------------------------- | --------------------------------------------------------------------- |
| `adaptiveVector` | adaptive icon, vector foreground, `<monochrome>` reusing it | the vector rewrite, and the monochrome layer that needs a redirect     |
| `adaptiveRaster` | adaptive icon, PNG foreground, `<monochrome>` PNG of its own | pixels behind an adaptive layer, and a monochrome bannered in place    |
| `legacyRaster`   | `@mipmap/app_icon`, WebP only, no adaptive icon           | the silhouette clip, the resolved WebP reader, and unconventional names |
| `plainVector`    | `@drawable/ic_launcher`, no adaptive icon, no round variant | an unmasked vector, and a round icon that resolves to nothing          |
| `prod`           | `adaptiveVector`'s icons                                  | no banner configured: the icon is the checked-in artwork               |

All four bannered flavors carry the same text and the same style, so the icon is the only variable —
anything that differs between two of them is the icon kind's doing. Each has its own
`applicationIdSuffix` and label, `prod` included, because comparing them means having all five
installed at once — there is no aggregate install task, so that is five `:app:install<Flavor>Debug`
runs. `MainActivity` is empty and exists only to give each of them a launcher entry.

Two things about the layout are load-bearing. The launcher icons live in the flavors rather than in
`main`, because resource merging can override a file but not remove one, so a flavor sharing `main`
could never be the project that has *no* adaptive icon. And `android:icon` is declared per flavor for
the same reason in the manifest: the plugin reads the source manifests at AGP's source-set
precedence, and does not interpret `tools:remove`, so a `roundIcon` declared in `main` is a name every
flavor then has to resolve.

`app/src/adaptiveRaster/res` and `app/src/legacyRaster/res` are derived from `adaptiveVector`'s
vectors by [`scripts/demo-icons.sh`](scripts/demo-icons.sh) rather than drawn by hand.

**A JDK 17 has to be installed.** The plugin is compiled for 17, the floor AGP 9 sets, and pins that
with a Java toolchain; `plugin/settings.gradle.kts` configures no toolchain resolver, so Gradle
detects a 17 rather than downloading one. Building the demo app additionally needs the *included*
build to find it, which is why CI passes
`-Porg.gradle.java.installations.paths="$JAVA_HOME"` — do the same if a local 17 sits somewhere
Gradle does not look.

Design decisions and their reasoning are in [`specs/`](specs/icon-banner-gradle-plugin.md). Read the
relevant part before changing behaviour rather than inferring intent from the code.

## Publishing

Tag a version and the `Publish` workflow does the rest. The tag name without its leading `v` becomes
the version, via `-PpluginVersion`:

```bash
git tag v0.0.1 && git push origin v0.0.1
```

That publishes to two places. **GitHub Packages** takes any version including snapshots, and needs no
personal access token, because Actions injects a `GITHUB_TOKEN`. The **Gradle Plugin Portal** is what
consumers actually use, since it needs no credentials to read; it runs on tags only, because it
rejects `-SNAPSHOT` versions and a published version can never be replaced.

A `workflow_dispatch` run publishes a snapshot to GitHub Packages only, for shaking things out
without a tag.

Portal credentials live in two places, and never in the repository:

- CI: the `GRADLE_PUBLISH_KEY` and `GRADLE_PUBLISH_SECRET` Actions secrets.
- Locally: `gradle.publish.key` and `gradle.publish.secret` in `~/.gradle/gradle.properties`.

Check a release without publishing anything — this validates the metadata the Portal requires and
stops short of uploading:

```bash
./gradlew -p plugin publishPlugins --validate-only -PpluginVersion=0.0.1
```

The first version of a new plugin id goes through a one-time approval on the Portal before it
appears, so expect the initial release to lag.

### When a release half-fails

The Portal job runs after the GitHub Packages job, so the reachable bad state is "on Packages, not on
the Portal". Recover with **Re-run failed jobs**, which reruns only the Portal job. Do *not* use
*Re-run all jobs* and do not delete and re-push the tag: GitHub Packages returns 409 for a version
that already exists, so the rerun dies before it reaches the Portal. Delete the Packages version
first if you must take that route.

A version that reached the Portal can never be replaced. If a released artifact is wrong, the only
remedy is to release the next version.

Locally:

```bash
./gradlew -p plugin publishToMavenLocal   # to consume it from another project
./gradlew -p plugin publishAllPublicationsToGitHubPackagesRepository
```

The manual push needs a token with `write:packages` in `gpr.user` / `gpr.key`. Never commit one.

Consumers need a `read:packages` token too — GitHub requires auth on Maven reads even for public
packages. Moving to the Gradle Plugin Portal is what removes that.

## Previews

The README images are generated from the demo app, so they follow whatever `app/build.gradle.kts`
configures — change the corner, text or font and rerun:

```bash
./scripts/preview.sh              # docs/preview.png
./scripts/preview-monochrome.sh   # docs/preview-monochrome.webp
```

Both need `python3`, `inkscape` and `imagemagick`. `scripts/vd2svg.py` will also preview a single
drawable:

```bash
python3 scripts/vd2svg.py out.svg mono.xml --mask --tint '#D32F2F' --background '#F6DEDE'
```

It is a previewer rather than a real VectorDrawable renderer;
[`scripts/CLAUDE.md`](scripts/CLAUDE.md) lists what it does not cover. Output is reconstructed from
the generated XML, not captured from a device — for anything load-bearing, install the app and look
at it.
