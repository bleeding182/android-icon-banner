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
