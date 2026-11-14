# funekit

The UI kit funeplayer and funeCOMP share: scale, tokens, touch, motion, page stack, rows, overlays, Quickplay and the
now playing layout, in the form funeOS draws them.

Each app takes it as a submodule at `kit/` and pins one commit in `kit.pin` (`tools/kitset.sh`, `funekit.pin`).

The `player/` files under `tokens/funeos*/` are funeOS files, copied unchanged; the pin and bridge files beside them are the kit's own. Selawik ships with its OFL text
(`kit/src/main/assets/OFL-Selawik.txt`).

GPL-3.0-or-later (`LICENSE`).
