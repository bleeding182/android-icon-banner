# Third-party material

The plugin itself is [MIT](LICENSE) licensed. The published artifact contains no third-party code and
no font data — this file covers material in the source repository, plus what the plugin causes *your*
app to contain.

## In this repository

**Roboto Mono Bold** — `plugin/src/test/resources/font/RobotoMono-Bold.ttf`

Copyright 2015 The Roboto Mono Project Authors
(https://github.com/googlefonts/robotomono), licensed under the SIL Open Font License 1.1. The full
licence is checked in beside it as `plugin/src/test/resources/font/OFL.txt`.

Test fixture only. It gives the test suite a real face to trace so it never touches the network, and
it is not on any compile or runtime classpath — the published jar contains no font bytes.

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
