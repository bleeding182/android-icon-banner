# Third-party material

The plugin itself is [MIT](LICENSE) licensed. The published artifact contains no third-party code and
no font data — this file covers material in the source repository, what your build resolves while it
runs, and what the plugin causes *your* app to contain.

## In this repository

**Roboto Mono Bold** — `plugin/src/test/resources/font/RobotoMono-Bold.ttf`

Copyright 2015 The Roboto Mono Project Authors
(https://github.com/googlefonts/robotomono), licensed under the SIL Open Font License 1.1. The full
licence is checked in beside it as `plugin/src/test/resources/font/OFL.txt`.

Test fixture only. It gives the test suite a real face to trace so it never touches the network, and
it is not on any compile or runtime classpath — the published jar contains no font bytes.

## Resolved by your build

**TwelveMonkeys ImageIO WebP reader** — `com.twelvemonkeys.imageio:imageio-webp:3.14.0`, with its five
siblings at the same version: `com.twelvemonkeys.imageio:imageio-core`,
`com.twelvemonkeys.imageio:imageio-metadata`, `com.twelvemonkeys.common:common-lang`,
`com.twelvemonkeys.common:common-io` and `com.twelvemonkeys.common:common-image`. Six jars, and nothing
outside that group.

Copyright (c) 2008-2020, Harald Kuhr, licensed under the 3-clause BSD licence. The POM declares only
"The BSD License", which is ambiguous between the two- and three-clause forms; the upstream
`LICENSE.txt` is the three-clause text, so BSD-3-Clause is what is cited here.

Android Studio writes the legacy launcher mipmaps as WebP and the JDK ships no reader for the format,
so the plugin resolves this one from your project's `iconBannerImageReaders` configuration the first
time the JDK's own readers fail on one of your bitmaps. It runs inside the Gradle daemon at build
time only: **no code from it is redistributed** — not in the plugin's published jar, and not in your
application. Unlike the fonts below, where traced glyph outlines really do end up embedded in your
icon, nothing of this library reaches your APK. It is on the plugin's own test classpath too, so the
suite can decode a real webp icon without a build.

A project whose launcher icon is all vectors, or all PNG, never needs it.

## In apps built with the plugin

No font file is redistributed. The glyphs used by your banner text are traced to vector path data,
and that path data is written into a copy of your launcher icon.

Under the SIL Open Font License, that path data is not Font Software: artwork produced with a font is
yours, and embedding a font in a document does not change the document's licence
([OFL-FAQ 1.1.1 and 1.13](https://openfontlicense.org/ofl-faq/)). For an OFL family — which most of
Google Fonts is, including the default Roboto Mono — you owe no notice and no attribution.

Families licensed under Apache-2.0 or the Ubuntu Font Licence have no comparable carve-out, and
whether traced outlines constitute a derivative work of the font program is unsettled. If you ship a
banner rendered in one of those, including the notice on your licences screen is the conservative
choice. Each family's licence is listed on https://fonts.google.com.

None of this is legal advice.
