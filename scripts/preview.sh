#!/usr/bin/env bash
# Regenerates docs/preview.png, the image in the README: production icon, staging icon as a launcher
# masks it, and the full adaptive foreground the plugin generated.
#
# Requires python3, inkscape and imagemagick. See CLAUDE.md in this directory.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

cd "$root"
./gradlew :app:assembleStagingDebug -q

res="app/src/main/res/drawable"
generated="app/build/generated/res/generateStagingDebugIconBanner/drawable"
background="$res/ic_launcher_background.xml"

if [[ ! -f "$generated/ic_launcher_foreground.xml" ]]; then
    echo "No generated foreground. Is a banner configured for stagingDebug in app/build.gradle.kts?" >&2
    exit 1
fi

# Production ships the unmodified foreground; staging ships the generated one.
python3 scripts/vd2svg.py "$work/prod.svg" "$background" "$res/ic_launcher_foreground.xml" --mask
python3 scripts/vd2svg.py "$work/staging.svg" "$background" "$generated/ic_launcher_foreground.xml" --mask
python3 scripts/vd2svg.py "$work/full.svg" "$background" "$generated/ic_launcher_foreground.xml"

for name in prod staging full; do
    inkscape --export-type=png --export-filename="$work/$name.png" "$work/$name.svg" 2>/dev/null
done

mkdir -p docs
magick montage "$work/prod.png" "$work/staging.png" "$work/full.png" \
    -tile 3x1 -geometry +24+24 -background none docs/preview.png

echo "Wrote docs/preview.png"
