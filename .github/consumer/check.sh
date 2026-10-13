#!/usr/bin/env bash
# What a user gets: the plugin as published, applied by id and version to a project shaped like a
# fresh Android Studio template. The TestKit suite puts the plugin on its fixtures' buildscript
# classpath, so the marker POM, the module metadata and its org.gradle.jvm.version are never
# exercised there — and those are exactly what a wrong jvmTarget breaks.
#
#   .github/consumer/check.sh <plugin-version> <maven-repo> [work-dir]
#
# <maven-repo> is a Maven repository the plugin was published to, e.g. by
#   ./gradlew -p plugin publishToMavenLocal -PpluginVersion=<v> -Dmaven.repo.local=<maven-repo>
# The consumer resolves the plugin from there and nowhere else. It is built in [work-dir] (default: a
# fresh temporary directory), never in the repository, so nothing checked in can prop it up.
#
# Run it on the JVM you mean to prove: the consumer has no gradle-daemon-jvm.properties, so its
# daemon is JAVA_HOME. CI uses 17, the floor AGP 9 supports. Needs ANDROID_HOME.
set -euo pipefail

fail() { echo "FAIL: $*" >&2; exit 1; }

[ $# -ge 2 ] && [ $# -le 3 ] || fail "usage: $0 <plugin-version> <maven-repo> [work-dir]"
version="$1"
repo="$(cd "$2" && pwd)" || fail "no maven repository at $2"
work="${3:-$(mktemp -d)}"

case "$version" in
  "" | *[!A-Za-z0-9._-]*) fail "version '$version' must be only [A-Za-z0-9._-]" ;;
esac
# Both end up inside sed replacements and a Kotlin string literal.
case "$repo" in
  *[\|\&\\\"\$]*) fail "maven repo path '$repo' contains a character this script will not quote" ;;
esac
[ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME" ] || fail "ANDROID_HOME is not an Android SDK"

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/../.." && pwd)"

# --- The published artifacts --------------------------------------------------------------------

module_dir="$repo/io/github/bleeding182/icon-banner-gradle-plugin/$version"
module="$module_dir/icon-banner-gradle-plugin-$version.module"
jar="$module_dir/icon-banner-gradle-plugin-$version.jar"
marker="$repo/io/github/bleeding182/iconbanner/io.github.bleeding182.iconbanner.gradle.plugin/$version"

[ -f "$module" ] || fail "no module metadata at $module"
grep -q '"org.gradle.jvm.version": 17' "$module" \
  || fail "module metadata must declare org.gradle.jvm.version 17, or a JDK 17 daemon cannot select a variant: $module"
[ -d "$marker" ] || fail "no plugin marker at $marker; plugins { id(...) version ... } cannot resolve"

# The JDK floor, on the jar that is published rather than whatever sits in build/libs: a class file
# above major 61 is an UnsupportedClassVersionError for a consumer on a supported daemon.
[ -f "$jar" ] || fail "no plugin jar at $jar"
javap="${JAVA_HOME:+$JAVA_HOME/bin/}javap"
mapfile -t classes < <(unzip -Z1 "$jar" '*.class' | sed 's/\.class$//; s|/|.|g')
[ "${#classes[@]}" -gt 0 ] || fail "no classes in $jar"
majors="$("$javap" -verbose -cp "$jar" "${classes[@]}" | awk '/major version/ { print $3 }' | sort -u | tr '\n' ' ')"
[ "$majors" = "61 " ] || fail "expected class-file major 61 (JDK 17) only in $jar, found: $majors"
echo "ok: $jar targets JDK 17, module metadata and plugin marker present"

# --- The consumer project -----------------------------------------------------------------------

# Never cleaned here: it is a path someone typed. A non-empty one is refused instead.
[ ! -e "$work" ] || [ -z "$(ls -A "$work")" ] || fail "work dir $work is not empty"
mkdir -p "$work"
cp -R "$here/." "$work/"
rm -f "$work/check.sh"
# The template's icons, not new ones: the demo app's adaptiveVector flavor is Android Studio's
# checked-in set — adaptive icon, vector foreground shared by <monochrome>, WebP legacy mipmaps,
# roundIcon — so here it is a consumer's main source set.
mkdir -p "$work/app/src/main"
cp -R "$root/app/src/adaptiveVector/res" "$work/app/src/main/res"
cp "$root/app/src/adaptiveVector/AndroidManifest.xml" "$work/app/src/main/AndroidManifest.xml"
sed -i "s|@PLUGIN_VERSION@|$version|" "$work/app/build.gradle.kts"
sed -i "s|@MAVEN_REPO@|$repo|" "$work/settings.gradle.kts"
echo "sdk.dir=$ANDROID_HOME" > "$work/local.properties"

# The repository's wrapper, but the consumer as the build root.
"$root/gradlew" -p "$work" :app:assembleStagingDebug :app:assembleProdDebug

generated="$work/app/build/generated/res/generateStagingDebugIconBanner/drawable/ic_launcher_foreground.xml"
[ -f "$generated" ] || fail "no generated foreground at $generated"
grep -qi 'ff00ff' "$generated" || fail "the generated foreground has no banner in it: $generated"
echo "ok: the published plugin bannered stagingDebug"
