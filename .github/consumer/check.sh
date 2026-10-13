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

# --- The APKs -----------------------------------------------------------------------------------
#
# Asserted on what gets installed, not on the plugin's intermediate output: a generated file that
# loses the resource merge, or a manifest pointing elsewhere, would pass a check on build/generated
# and still ship the plain icon.

build_tools="$ANDROID_HOME/build-tools/$(ls "$ANDROID_HOME/build-tools" | sort -V | tail -n 1)"
aapt2="$build_tools/aapt2"
[ -x "$aapt2" ] || fail "no aapt2 at $aapt2"
echo "using $aapt2"

failures=0
check() { # <description> <command...>
  local what="$1"; shift
  if "$@"; then echo "ok: $what"; else echo "FAIL: $what" >&2; failures=$((failures + 1)); fi
}

# Every entry of the APK's resource table, `dump resources` style: "resource 0x7f020000 mipmap/x".
resources() { "$aapt2" dump resources "$1"; }
# The id an element's attribute references in a compiled XML file, e.g. 0x7f010004.
xml_ref() { # <apk> <file> <element> <attr>
  "$aapt2" dump xmltree --file "$2" "$1" | awk -v el="$3" -v attr=":$4(" '
    $1 == "E:" { in_el = ($2 == el) }
    in_el && $1 == "A:" && index($2, attr) { v = $2; sub(/.*\)=@/, "", v); print v; exit }'
}
refers_to() { # <apk> <file> <element> <attr> <expected type/name>
  local id name
  id="$(xml_ref "$1" "$2" "$3" "$4")"
  name="$(resources "$1" | awk -v id="$id" '$1 == "resource" && $2 == id { print $3; exit }')"
  [ -n "$id" ] && [ "$name" = "$5" ] \
    || { echo "  $2: <$3 android:$4> is ${name:-${id:-unset}}, expected $5" >&2; return 1; }
}
icon_is() { # <apk> <path>
  local line; line="$("$aapt2" dump badging "$1" | grep '^application:')"
  grep -qF "icon='$2'" <<<"$line" || { echo "  badging: $line" >&2; return 1; }
}
# Producers are captured before grep sees them: under pipefail, `producer | grep -q` lets grep exit
# on the first match and SIGPIPE the producer, which fails a positive check and passes a negated one.
entries() { unzip -Z1 "$1"; }
in_apk() { grep -qxF "$2" <<<"$(entries "$1")" || { echo "  $2 is not in the APK" >&2; return 1; }; }
not_in_apk() { ! grep -qxF "$2" <<<"$(entries "$1")" || { echo "  $2 is in the APK" >&2; return 1; }; }
png() { # <apk> <file>: present, and a PNG by its signature rather than by its name
  in_apk "$1" "$2" || return 1
  local magic; magic="$(unzip -p "$1" "$2" | od -An -tx1 -N8 | tr -d ' \n')" || true
  [ "$magic" = 89504e470d0a1a0a ] || { echo "  $2 does not start with a PNG signature" >&2; return 1; }
}
xmltree() { # <apk> <file>: fails, rather than printing nothing, when the file is missing
  "$aapt2" dump xmltree --file "$2" "$1" || { echo "  could not dump $2" >&2; return 1; }
}
banner_colour() { # <apk> <file>: the banner's #FF00FF, as aapt2 prints a compiled colour
  local tree; tree="$(xmltree "$1" "$2")" || return 1
  grep -qF '=#ffff00ff' <<<"$tree" || { echo "  $2 does not contain #FF00FF" >&2; return 1; }
}
no_banner_colour() {
  local tree; tree="$(xmltree "$1" "$2")" || return 1
  ! grep -qF '=#ffff00ff' <<<"$tree" || { echo "  $2 contains #FF00FF" >&2; return 1; }
}
no_mono() { ! grep -q '_iconbanner_mono' <<<"$(entries "$1")" || { echo "  a *_iconbanner_mono drawable is in the APK" >&2; return 1; }; }

apk_dir="$work/app/build/outputs/apk"
for flavor in staging prod; do
  apk="$apk_dir/$flavor/debug/app-$flavor-debug.apk"
  [ -f "$apk" ] || fail "no APK at $apk"
  echo "--- $flavor: $apk"

  check "$flavor: android:icon is res/mipmap-anydpi-v26/ic_launcher.xml" \
    icon_is "$apk" res/mipmap-anydpi-v26/ic_launcher.xml
  check "$flavor: android:roundIcon is mipmap/ic_launcher_round" \
    refers_to "$apk" AndroidManifest.xml application roundIcon mipmap/ic_launcher_round
  check "$flavor: res/mipmap-anydpi-v26/ic_launcher_round.xml is packaged" \
    in_apk "$apk" res/mipmap-anydpi-v26/ic_launcher_round.xml

  # The template's <monochrome> shares the foreground drawable, which in staging now carries a
  # coloured banner; the themed icon has to be pointed at a cutout of its own instead.
  if [ "$flavor" = staging ]; then
    mono=drawable/ic_launcher_foreground_iconbanner_mono
  else
    mono=drawable/ic_launcher_foreground
  fi
  for icon in ic_launcher ic_launcher_round; do
    file="res/mipmap-anydpi-v26/$icon.xml"
    check "$flavor: $file <foreground> is drawable/ic_launcher_foreground" \
      refers_to "$apk" "$file" foreground drawable drawable/ic_launcher_foreground
    check "$flavor: $file <monochrome> is $mono" \
      refers_to "$apk" "$file" monochrome drawable "$mono"
  done

  if [ "$flavor" = staging ]; then
    check "$flavor: res/drawable/ic_launcher_foreground.xml has the banner colour #FF00FF" \
      banner_colour "$apk" res/drawable/ic_launcher_foreground.xml
    check "$flavor: res/drawable/ic_launcher_foreground_iconbanner_mono.xml is packaged" \
      in_apk "$apk" res/drawable/ic_launcher_foreground_iconbanner_mono.xml
  else
    check "$flavor: res/drawable/ic_launcher_foreground.xml has no banner colour" \
      no_banner_colour "$apk" res/drawable/ic_launcher_foreground.xml
    check "$flavor: no *_iconbanner_mono drawable is packaged" no_mono "$apk"
  fi

  # The template's legacy mipmaps are WebP; a bannered one comes back as a PNG of the same name.
  for density in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
    for icon in ic_launcher ic_launcher_round; do
      base="res/mipmap-$density-v4/$icon"
      if [ "$flavor" = staging ]; then
        check "$flavor: $base.png is packaged, a PNG" png "$apk" "$base.png"
        check "$flavor: $base.webp is gone" not_in_apk "$apk" "$base.webp"
      else
        check "$flavor: $base.webp is still the template's" in_apk "$apk" "$base.webp"
        check "$flavor: no $base.png" not_in_apk "$apk" "$base.png"
      fi
    done
  done
done

[ "$failures" -eq 0 ] || fail "$failures APK check(s) failed; the consumer build is in $work"
echo "ok: every APK check passed"
