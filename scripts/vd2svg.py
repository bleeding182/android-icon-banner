#!/usr/bin/env python3
"""Flatten an Android VectorDrawable into plain SVG, for previewing generated icons.

A previewer, not a conformant renderer. See scripts/CLAUDE.md for what it does not cover.

Usage:
    vd2svg.py OUT.svg LAYER.xml [LAYER.xml ...] [options]

Layers are drawn in the order given, so background first.

Options:
    --mask              Clip to the adaptive-icon circle, approximating what a launcher shows.
    --tint COLOR        Ignore declared fills and paint everything COLOR, keeping alpha. This is
                        what the system does to a <monochrome> layer.
    --background COLOR  Fill the masked area with COLOR first, so cutouts have something to show.
"""
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"


def attr(element, name, default=None):
    return element.get(ANDROID + name, default)


def number(element, name, default=0.0):
    try:
        return float(attr(element, name, default))
    except (TypeError, ValueError):
        return default


AAPT = "{http://schemas.android.com/aapt}"


def split_argb(colour):
    """#AARRGGBB or #RRGGBB to (#RRGGBB, alpha)."""
    if colour.startswith("#") and len(colour) == 9:
        return "#" + colour[3:], int(colour[1:3], 16) / 255.0
    return colour, 1.0


class Converter:
    def __init__(self, tint=None):
        self.tint = tint
        self.clip_id = 0
        self.gradient_id = 0
        self.definitions = []

    def gradient(self, element):
        """A linear <gradient> nested in an aapt:attr, as an SVG def. Returns a url(...) or None."""
        for holder in element.iter(AAPT + "attr"):
            if holder.get("name") != "android:fillColor":
                continue
            for gradient in holder.iter("gradient"):
                # Radial and sweep exist too; the plugin never emits either, so they are not worth
                # guessing at. Fall through to the flat fill instead of rendering something wrong.
                if attr(gradient, "type", "linear") != "linear":
                    return None
                self.gradient_id += 1
                name = f"grad{self.gradient_id}"
                stops = ""
                for item in gradient.iter("item"):
                    colour, alpha = split_argb(attr(item, "color", "#000000"))
                    # A monochrome layer keeps only alpha, so the ramp survives but the hue does not.
                    stops += (
                        f'<stop offset="{number(item, "offset"):g}" '
                        f'stop-color="{self.tint or colour}" stop-opacity="{alpha:g}"/>'
                    )
                self.definitions.append(
                    f'<linearGradient id="{name}" gradientUnits="userSpaceOnUse" '
                    f'x1="{number(gradient, "startX"):g}" y1="{number(gradient, "startY"):g}" '
                    f'x2="{number(gradient, "endX"):g}" y2="{number(gradient, "endY"):g}">'
                    f"{stops}</linearGradient>"
                )
                return f"url(#{name})"
        return None

    def fill(self, element):
        rule = "evenodd" if attr(element, "fillType") == "evenOdd" else "nonzero"
        opacity = number(element, "fillAlpha", 1.0)

        reference = self.gradient(element)
        if reference:
            return f'fill="{reference}" fill-opacity="{opacity:g}" fill-rule="{rule}"'

        colour = attr(element, "fillColor") or "none"
        colour, alpha = split_argb(colour)
        opacity *= alpha
        if self.tint and colour != "none":
            colour = self.tint
        return f'fill="{colour}" fill-opacity="{opacity:g}" fill-rule="{rule}"'

    @staticmethod
    def transform(group):
        """Android applies scale, then rotation, then translation, all about the pivot."""
        pivot_x, pivot_y = number(group, "pivotX"), number(group, "pivotY")
        rotation = number(group, "rotation")
        scale_x, scale_y = number(group, "scaleX", 1.0), number(group, "scaleY", 1.0)
        translate_x, translate_y = number(group, "translateX"), number(group, "translateY")
        if (rotation, scale_x, scale_y, translate_x, translate_y) == (0.0, 1.0, 1.0, 0.0, 0.0):
            return ""
        parts = [f"translate({translate_x + pivot_x:g},{translate_y + pivot_y:g})"]
        if rotation:
            parts.append(f"rotate({rotation:g})")
        if (scale_x, scale_y) != (1.0, 1.0):
            parts.append(f"scale({scale_x:g},{scale_y:g})")
        parts.append(f"translate({-pivot_x:g},{-pivot_y:g})")
        return f' transform="{" ".join(parts)}"'

    def children(self, parent):
        """Render a <vector> or <group>'s children, honouring any <clip-path> siblings."""
        out = []
        clips = [c for c in parent if c.tag == "clip-path"]
        for child in parent:
            if child.tag == "path":
                data = attr(child, "pathData")
                if data:
                    out.append(f'<path d="{data}" {self.fill(child)}/>')
            elif child.tag == "group":
                out.append(f"<g{self.transform(child)}>{self.children(child)}</g>")
        body = "".join(out)

        # A <clip-path> constrains everything else in the same group.
        for clip in clips:
            data = attr(clip, "pathData")
            if not data:
                continue
            self.clip_id += 1
            name = f"clip{self.clip_id}"
            rule = "evenodd" if attr(clip, "fillType") == "evenOdd" else "nonzero"
            body = (
                f'<clipPath id="{name}"><path d="{data}" clip-rule="{rule}"/></clipPath>'
                f'<g clip-path="url(#{name})">{body}</g>'
            )
        return body


def main(argv):
    options = {"--tint": None, "--background": None}
    args, mask, index = [], False, 1
    while index < len(argv):
        token = argv[index]
        if token == "--mask":
            mask = True
        elif token in options:
            index += 1
            options[token] = argv[index]
        else:
            args.append(token)
        index += 1

    if len(args) < 2:
        print(__doc__, file=sys.stderr)
        return 2

    destination, layers = args[0], args[1:]
    converter = Converter(tint=options["--tint"])

    width = height = 108.0
    body = ""
    for layer in layers:
        root = ET.parse(layer).getroot()
        width = number(root, "viewportWidth", width)
        height = number(root, "viewportHeight", height)
        body += converter.children(root)

    radius = min(width, height) / 3.0  # the 72dp mask of a 108dp canvas
    if options["--background"]:
        backdrop = (
            f'<circle cx="{width / 2:g}" cy="{height / 2:g}" r="{radius:g}" '
            f'fill="{options["--background"]}"/>'
        ) if mask else f'<rect width="{width:g}" height="{height:g}" fill="{options["--background"]}"/>'
        body = backdrop + body
    if mask:
        body = (
            f'<clipPath id="mask"><circle cx="{width / 2:g}" cy="{height / 2:g}" r="{radius:g}"/>'
            f"</clipPath><g clip-path=\"url(#mask)\">{body}</g>"
        )

    defs = f"<defs>{''.join(converter.definitions)}</defs>" if converter.definitions else ""
    with open(destination, "w") as handle:
        handle.write(
            f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width:g} {height:g}" '
            f'width="216" height="216">{defs}{body}</svg>'
        )
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
