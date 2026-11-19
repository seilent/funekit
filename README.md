# funekit

The UI kit funeplayer and funeCOMP share: scale, tokens, touch, motion, page stack, rows, overlays, Quickplay and the
now playing layout, in the form funeOS draws them; the player and library seam (`:api`), a made up library for debug
and test builds (`:fake`) and the music screens (`:screens`).

Each app takes it as a submodule at `kit/` and pins one commit in `kit.pin` (`tools/kitset.sh`, `funekit.pin`).

The `player/` files under `tokens/funeos/` are funeOS files, copied unchanged; the `SOURCE` pin beside them is the kit's
own. Selawik ships with its OFL text (`kit/src/main/assets/OFL-Selawik.txt`).

The AutoEq profiles and index in `core/src/test/resources/autoeq/`, `artist/src/test/resources/autoeq/` and the
`aeq-*` fixtures are AutoEq's, MIT (`LICENSE-AutoEq`).

GPL-3.0-or-later (`LICENSE`).
