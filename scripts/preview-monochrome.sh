#!/usr/bin/env bash
# Regenerates docs/preview-monochrome.webp: the themed (monochrome) icon cycling through three
# system tints, showing that the banner text stays a genuine cutout whatever colour it is tinted.
#
# Requires python3, inkscape and imagemagick. See CLAUDE.md in this directory.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

cd "$root"
./gradlew :app:assembleStagingDebug -q

generated="app/build/generated/res/generateStagingDebugIconBanner/drawable"
mono="$(ls "$generated"/*_iconbanner_mono.xml 2>/dev/null | head -1 || true)"

if [[ -z "$mono" ]]; then
    echo "No generated monochrome drawable. Does the icon have a <monochrome> layer?" >&2
    exit 1
fi

# A themed icon is the monochrome layer tinted with the wallpaper's accent, on a matching
# surface. Cutouts show that surface through, which is the point of the animation.
tints=("#D32F2F:#F6DEDE" "#2E7D32:#DDEADE" "#1565C0:#DBE5F3")

frames=()
for index in "${!tints[@]}"; do
    tint="${tints[$index]%%:*}"
    background="${tints[$index]##*:}"
    python3 scripts/vd2svg.py "$work/$index.svg" "$mono" --mask --tint "$tint" --background "$background"
    inkscape --export-type=png --export-filename="$work/$index.png" "$work/$index.svg" 2>/dev/null
    frames+=("$work/$index.png")
done

mkdir -p docs
# Animated WebP, not GIF: the icon is masked to a circle, and GIF's one-bit alpha would snap every
# antialiased pixel on that rim to fully opaque or fully gone. Lossless, because the artwork is flat
# colour and the cutouts have to stay exactly transparent. 60 hundredths of a second per frame.
magick -delay 60 -loop 0 "${frames[@]}" \
    -define webp:lossless=true -define webp:alpha-quality=100 \
    docs/preview-monochrome.webp

echo "Wrote docs/preview-monochrome.webp"
