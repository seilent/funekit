#!/usr/bin/env python3
import json
from pathlib import Path

import numpy as np
from PIL import Image

W, H = 48, 16
BOARD = "fx"

CASES = {
    "press": ("press", False, [("row1.tile", 1, 0), ("row2.tile", 1, 1), ("row4.tile", 1, 0)], W),
    "prefix": ("prefix", False, [("row3.glow", 1, 0), ("row30.title", 1, 0), ("row3.title", 1, 1)], W),
    "toast": ("toast-500-press", False, [("toast.strip", 1, 0), ("toast.text", 1, 0), ("row2.tile", 0, 1)], W),
    "toastfixed": ("toast-500-press", False, [("toast.strip", 1, 0), ("toast.text", 1, 0), ("row2.tile", 1, 1)], W),
    "residual": ("resid", True, [("row1.tile", 1, 1)], W),
    "size": ("size", False, [("row1.tile", 1, 1)], W - 8),
}
STRAY = 5


def box(slot):
    return [2 + 8 * slot, 6, 6 + 8 * slot, 10]


def window(slot):
    return [8 * slot, 4, 8 + 8 * slot, 12]


def paint(img, slot):
    x0, y0, x1, y1 = box(slot)
    img[y0:y1, x0:x1] = 255


def save(img, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(img, "RGB").save(path, optimize=True)


def main():
    here = Path(__file__).parent
    (here / "tokens.txt").write_text("color.bg #000000\n")
    refs = {}
    for case, (kind, residual, regions, ref_w) in CASES.items():
        tile = np.zeros((H, W, 3), dtype=np.uint8)
        ref = np.zeros((H, ref_w, 3), dtype=np.uint8)
        spec = []
        for slot, (name, fp, fo) in enumerate(regions):
            if fp:
                paint(tile, slot)
            if fo:
                paint(ref, slot)
            spec.append({"name": name, "kind": "tile", "box": box(slot), "window": window(slot), "color": "#ffffff", "alpha": 1.0, "ref": None, "exclude": None, "hidden": False, "band": None})
        if residual:
            paint(ref, STRAY)
        stem = f"{BOARD}-{kind}"
        save(tile, here / case / f"{stem}.png")
        doc = {"tile": f"{stem}.png", "board": BOARD, "size": [W, H], "tolerance": 1, "margin": 2, "bands": {"status": [], "nav": []}, "residual": {"on": residual, "why": "fixture"}, "regions": spec}
        (here / case / f"{stem}.regions.json").write_text(json.dumps(doc, indent=1) + "\n")
        if kind in refs and not np.array_equal(refs[kind][0], ref):
            raise SystemExit(f"make: cases {refs[kind][1]} and {case} share {kind} with different references")
        refs.setdefault(kind, (ref, case))
    for kind, (ref, _) in refs.items():
        save(ref, here / "ref" / BOARD / f"{kind}.png")


if __name__ == "__main__":
    main()
