#!/usr/bin/env bash
# Regenerates docs/preview.png, the image in the README: the unbannered icon, the bannered one as a
# launcher masks it, and the full adaptive foreground the plugin generated.
#
# Reads the adaptiveVector flavor, which is the demo's plain Android Studio icon set.
#
# Requires python3, inkscape and imagemagick. See CLAUDE.md in this directory.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

cd "$root"
./gradlew :app:assembleAdaptiveVectorDebug -q

res="app/src/adaptiveVector/res/drawable"
generated="app/build/generated/res/generateAdaptiveVectorDebugIconBanner/drawable"
background="$res/ic_launcher_background.xml"

if [[ ! -f "$generated/ic_launcher_foreground.xml" ]]; then
    echo "No generated foreground. Is a banner configured for adaptiveVectorDebug in app/build.gradle.kts?" >&2
    exit 1
fi

# prod ships the unmodified foreground; every bannered flavor ships a generated one.
python3 scripts/vd2svg.py "$work/prod.svg" "$background" "$res/ic_launcher_foreground.xml" --mask
python3 scripts/vd2svg.py "$work/bannered.svg" "$background" "$generated/ic_launcher_foreground.xml" --mask
python3 scripts/vd2svg.py "$work/full.svg" "$background" "$generated/ic_launcher_foreground.xml"

for name in prod bannered full; do
    inkscape --export-type=png --export-filename="$work/$name.png" "$work/$name.svg" 2>/dev/null
done

mkdir -p docs
magick montage "$work/prod.png" "$work/bannered.png" "$work/full.png" \
    -tile 3x1 -geometry +24+24 -background none docs/preview.png

echo "Wrote docs/preview.png"
