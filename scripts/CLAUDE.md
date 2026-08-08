# scripts/

Throwaway tooling for looking at generated icons. Not part of the plugin, not on any build path.

## Regenerating the README images

```bash
./scripts/preview.sh              # docs/preview.png            production / staging / full foreground
./scripts/preview-monochrome.sh   # docs/preview-monochrome.webp themed icon cycling three tints
```

Both need `python3`, `inkscape` and `imagemagick` on PATH, and both build `:app` first. They render
whatever `app/build.gradle.kts` currently configures, so the images track the demo app's settings —
change the corner, text or font there and rerun.

The animation is WebP rather than GIF, and should stay that way: the icon is masked to a circle, and
GIF carries one bit of alpha, so every antialiased pixel on that rim snaps to fully opaque or fully
gone. WebP keeps all 256 levels — measured, 173 distinct ones against the GIF's 2.

## Previewing one drawable

```bash
python3 scripts/vd2svg.py out.svg background.xml foreground.xml --mask
python3 scripts/vd2svg.py out.svg mono.xml --mask --tint '#D32F2F' --background '#F6DEDE'
```

Layers draw in the order given. `--mask` clips to the adaptive-icon circle. `--tint` repaints every
fill in one colour while keeping alpha, which is what the system does to a `<monochrome>` layer;
`--background` fills the masked area first, so cutouts have something to show through.

`<clip-path>` and `<group>` transforms are both handled, which the monochrome output needs — it is
defined entirely by a clip, and without one you would see icon content bleeding across the band that
a device never draws.

## What this is not

`vd2svg.py` is a previewer, not a conformant VectorDrawable renderer.

- **Gradients** (`aapt:attr` inline resources) are dropped. It warns on stderr.
- **The mask is a circle**, which is this script's guess. Real launchers use squircles, teardrops and
  rounded squares. A circle is the most aggressive common shape, so it is a useful conservative check
  for content clipped at the rim — but it is not what any specific device draws.
- **The themed-icon colours are invented.** Android derives them from the wallpaper palette; the
  three in `preview-monochrome.sh` were picked to look plausible and to differ from each other.
- **Trailing detail** — stroke attributes, `trimPath`, and anything else the plugin does not emit —
  is unimplemented rather than approximated.

Output is reconstructed from generated XML, not captured from a device. For anything load-bearing,
install the app and look at it.
