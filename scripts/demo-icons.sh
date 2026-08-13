#!/usr/bin/env bash
# Regenerates the demo app's derived icon assets — everything under app/src that is not hand-written.
#
# The four bannered flavors are meant to differ in exactly one thing: the kind of icon the plugin is
# pointed at. That only holds if the artwork itself is the same, so every flavor's icons are derived
# from adaptiveVector's, which is the checked-in Android Studio set.
#
# Requires python3, inkscape and imagemagick. See CLAUDE.md in this directory.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

cd "$root"
vector="app/src/adaptiveVector/res"
raster="app/src/adaptiveRaster/res"
legacy="app/src/legacyRaster/res"

# One SVG per layer, rasterized once per density. The monochrome layer keeps alpha and nothing else,
# so the tint it is flattened with is arbitrary — black is simply what reads on a light background
# when you open the file to check it.
python3 scripts/vd2svg.py "$work/foreground.svg" "$vector/drawable/ic_launcher_foreground.xml"
python3 scripts/vd2svg.py "$work/monochrome.svg" "$vector/drawable/ic_launcher_foreground.xml" \
    --tint '#000000'

# density : adaptive layer px (108dp). The legacy sizes come from the source webp, untouched.
for row in mdpi:108 hdpi:162 xhdpi:216 xxhdpi:324 xxxhdpi:432; do
    IFS=: read -r density adaptive <<<"$row"
    mkdir -p "$raster/mipmap-$density" "$legacy/mipmap-$density"

    for layer in foreground monochrome; do
        inkscape --export-type=png --export-filename="$work/$layer-$density.png" \
            --export-width="$adaptive" --export-height="$adaptive" "$work/$layer.svg" 2>/dev/null
        magick "$work/$layer-$density.png" -strip "$raster/mipmap-$density/ic_launcher_$layer.png"
    done

    for name in ic_launcher ic_launcher_round; do
        # adaptiveRaster's pre-26 fallback is PNG, so the JDK's own reader decodes it and no WebP
        # reader is resolved for that flavor. legacyRaster keeps the WebP bytes, which is the case
        # that needs one.
        source="$vector/mipmap-$density/$name.webp"
        magick "$source" -strip "$raster/mipmap-$density/$name.png"
        cp "$source" "$legacy/mipmap-$density/${name/ic_launcher/app_icon}.webp"
    done
done

echo "Wrote $raster and $legacy"
