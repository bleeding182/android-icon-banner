# Contributing

```bash
./gradlew -p plugin build          # plugin and its tests
./gradlew :app:assembleDevDebug    # demo app, bannered
./gradlew :app:assembleProdDebug   # demo app, untouched
```

The plugin lives in `plugin/`, its own Gradle build, included from the root build's plugin
management. `:app` exists only as a demo and a manual visual check.

Design decisions and their reasoning are in [`specs/`](specs/icon-banner-gradle-plugin.md). Read the
relevant part before changing behaviour rather than inferring intent from the code.

## Publishing

Releases go to GitHub Packages. Tag a version and the `Publish` workflow does the rest — it needs no
personal access token, because Actions injects a `GITHUB_TOKEN` and `permissions: packages: write`
is enough:

```bash
git tag v0.0.1 && git push origin v0.0.1
```

The tag name without its leading `v` becomes the version, via `-PpluginVersion`. A
`workflow_dispatch` run publishes a snapshot instead, for shaking things out without a tag.

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
./scripts/preview-monochrome.sh   # docs/preview-monochrome.gif
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
