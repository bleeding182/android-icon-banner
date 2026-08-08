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

## Publishing a test build

```bash
./gradlew -p plugin publishAllPublicationsToGitHubPackagesRepository
./gradlew -p plugin publishToMavenLocal      # or just locally
```

Credentials come from `gpr.user` / `gpr.key` in `~/.gradle/gradle.properties`, or from
`GITHUB_ACTOR` / `GITHUB_TOKEN`. Never commit either.

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
