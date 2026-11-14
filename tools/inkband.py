#!/usr/bin/env python3
import argparse
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

GATED = {"art", "ring", "bar", "tile", "glyph"}
PEAK_LEVELS = 4
PEAK_SHARE = 0.10
RESIDUAL_LEVELS = 16


def token_color(tokens, name):
    for line in Path(tokens).read_text().splitlines():
        parts = line.split()
        if len(parts) >= 2 and parts[0] == name:
            return rgb(parts[1])
    sys.exit(f"inkband: {name} not in {tokens}")


def rgb(hexcode):
    return np.array([int(hexcode[i:i + 2], 16) for i in (1, 3, 5)], dtype=np.int16)


def load(path):
    return np.asarray(Image.open(path).convert("RGB"), dtype=np.int16)


def hits(box, band):
    return bool(band) and box[1] < band[1] and box[3] > band[0]


def below(box, window, status, nav, h):
    box, window = list(box), list(window)
    top = hits(box, status)
    bottom = hits(box, nav)
    if top and box[1] <= 0:
        return None, None, "runs off the top through the status band", top, bottom
    if bottom and box[3] >= h:
        return None, None, "runs off the bottom through the nav band", top, bottom
    if top:
        box[1] = max(box[1], status[1])
        window[1] = max(window[1], status[1])
    if bottom:
        box[3] = min(box[3], nav[0])
        window[3] = min(window[3], nav[0])
    if box[3] <= box[1]:
        return None, None, "in the status band" if top else "in the nav band", top, bottom
    return box, window, None, top, bottom


def fill(mask, area, h, w):
    x0, y0, x1, y1 = area
    mask[max(0, y0):min(h, y1), max(0, x0):min(w, x1)] = True


def row_ground(img, free, bg):
    h = img.shape[0]
    med = np.zeros((h, 3), dtype=np.float32)
    have = np.zeros(h, dtype=bool)
    for y in range(h):
        px = img[y][free[y]]
        if px.size:
            med[y] = np.median(px, axis=0)
            have[y] = True
    rows = np.nonzero(have)[0]
    if rows.size == 0:
        med[:] = bg
        return med
    for c in range(3):
        med[:, c] = np.interp(np.arange(h), rows, med[rows, c])
    return med


def ink_box(img, mask, window, ref, threshold):
    x0, y0, x1, y1 = window
    diff = np.abs(img[y0:y1, x0:x1] - ref).max(axis=2)
    ink = (diff >= threshold) & ~mask[y0:y1, x0:x1]
    ys, xs = np.nonzero(ink)
    if xs.size == 0:
        return None
    return [int(xs.min()) + x0, int(ys.min()) + y0, int(xs.max()) + 1 + x0, int(ys.max()) + 1 + y0]


def peak(img, mask, box, window, ref):
    x0, y0, x1, y1 = window
    cut = img[y0:y1, x0:x1]
    free = ~mask[y0:y1, x0:x1]
    inside = np.zeros(free.shape, dtype=bool)
    inside[box[1] - y0:box[3] - y0, box[0] - x0:box[2] - x0] = True
    frame = free & ~inside
    local = np.median(cut[frame], axis=0) if frame.any() else ref
    body = free & inside
    if not body.any():
        return 0
    return int(np.abs(cut[body] - local).max())


def spills(box, window, size, top, bottom):
    w, h = size
    x0, y0, x1, y1 = window
    return (box[0] == x0 and x0 > 0) or (box[1] == y0 and y0 > 0 and not top) or (box[2] == x1 and x1 < w) or (box[3] == y1 and y1 < h and not bottom)


def fmt(box):
    return "-" if box is None else "{},{},{},{}".format(*box)


class Side:
    def __init__(self, name, img, mask, med):
        self.name = name
        self.img = img
        self.mask = mask
        self.med = med

    def lit(self, window, back, threshold):
        return np.abs(self.med[window[1]:window[3]] - back).max(axis=1) >= threshold


def check(regions_path, ref_dir, bg, rows, noref, residuals, unpeaked):
    spec = json.loads(Path(regions_path).read_text())
    tile = Path(regions_path).parent / spec["tile"]
    board = spec["board"]
    stem = tile.stem
    if not stem.startswith(board + "-"):
        return None
    kind = stem[len(board) + 1:]
    label = f"{board}/{kind}"
    ref = Path(ref_dir) / board / (kind + ".png")
    if not ref.exists():
        noref.append(label)
        return None
    fp = load(tile)
    fo = load(ref)
    if fp.shape != fo.shape:
        rows.append([label, "(tile)", "size", "MISS", f"{fp.shape[1]}x{fp.shape[0]}", f"{fo.shape[1]}x{fo.shape[0]}", "-", "sizes differ"])
        return label
    h, w = fp.shape[:2]
    glow = np.zeros((h, w), dtype=bool)
    windows = np.zeros((h, w), dtype=bool)
    for r in spec["regions"]:
        fill(windows, r["window"], h, w)
        if r["kind"] == "glow":
            fill(glow, r["window"], h, w)
    free = ~windows & ~glow
    sides = [Side("funeplayer", fp, glow, row_ground(fp, free, bg)), Side("funeOS", fo, glow, row_ground(fo, free, bg))]
    tolerance = spec["tolerance"]
    status = spec["bands"]["status"]
    nav = spec["bands"]["nav"]
    for r in spec["regions"]:
        name, kind_, box, window = r["name"], r["kind"], r["box"], r["window"]
        why = r["exclude"]
        peak_off = r.get("peak")
        if peak_off is not None and kind_ != "art":
            rows.append([label, name, kind_, "MISS", "-", "-", "-", "the peak is off only for an art region"])
            continue
        if why is None and kind_ == "glow":
            why = "press glow"
        top = bottom = False
        if why is None:
            box, window, why, top, bottom = below(box, window, status, nav, h)
        back = rgb(r["ref"]) if r["ref"] else bg
        contrast = r["alpha"] * float(np.abs(rgb(r["color"]) - back).max())
        threshold = contrast / 2
        if why is None and threshold < 1:
            why = "contrast under two levels"
        if why is None:
            lits = [s.lit(window, back, threshold) for s in sides]
            span = slice(box[1] - window[1], box[3] - window[1])
            if all(lit[span].all() for lit in lits):
                why = f"the measured ground reaches the half contrast ({threshold:.1f} levels) on every row of the box in both images"
        if why is not None:
            rows.append([label, name, kind_, "excluded", "-", "-", "-", why])
            continue
        over = [c for c in r.get("mask") or [] if c[0] < box[2] and c[2] > box[0] and c[1] < box[3] and c[3] > box[1]]
        if over:
            rows.append([label, name, kind_, "MISS", "-", "-", "-", "a mask covers the box at " + fmt(over[0])])
            continue
        boxes = []
        spilled = []
        peaks = []
        shares = []
        for s, lit in zip(sides, lits):
            m = s.mask.copy()
            m[window[1]:window[3], :] |= lit[:, None]
            for cover in r.get("mask") or []:
                fill(m, cover, h, w)
            b = ink_box(s.img, m, window, back, threshold)
            boxes.append(b)
            covered = int(lit[span].sum())
            if covered:
                shares.append(f"ground masks {covered} of {box[3] - box[1]} box rows in {s.name}")
            if b is not None:
                if spills(b, window, (w, h), top or bool(lit[0]), bottom or bool(lit[-1])):
                    spilled.append(s.name)
                peaks.append(peak(s.img, m, box, window, back))
        a, b = boxes
        gated = kind_ in GATED
        if a is None or b is None:
            side = " and ".join(s.name for s, x in zip(sides, boxes) if x is None)
            rows.append([label, name, kind_, "MISS" if gated else "text", fmt(a), fmt(b), "-", "; ".join([f"no ink in {side}"] + shares)])
            continue
        d = [b[i] - a[i] for i in range(4)]
        notes = []
        if spilled:
            notes.append("ink at the window edge in " + " and ".join(spilled))
        if gated:
            pa, pb = peaks
            peaked = abs(pa - pb) <= max(PEAK_LEVELS, PEAK_SHARE * max(pa, pb))
            if peak_off is not None:
                notes.append(f"peak {pa} against {pb} levels, off: {peak_off}")
                unpeaked.append(f"  {label} {name}: {peak_off}")
                peaked = True
            elif not peaked:
                notes.append(f"peak {pa} against {pb} levels")
            ok = max(abs(x) for x in d) <= tolerance and not spilled and peaked
            result = "ok" if ok else "MISS"
        else:
            notes.append(f"start {d[0]:+d} left, {d[3]:+d} bottom; end {d[2]:+d} right")
            result = "text"
        rows.append([label, name, kind_, result, fmt(a), fmt(b), "{:+d},{:+d},{:+d},{:+d}".format(*d), "; ".join(notes + shares)])
    on, reason = spec["residual"]["on"], spec["residual"]["why"]
    residuals.append(f"  {label}: {'on' if on else 'off'}, {reason}")
    if on:
        explained = glow.copy()
        for r in spec["regions"]:
            if r["hidden"]:
                continue
            fill(explained, r["window"], h, w)
            if r["band"]:
                fill(explained, r["band"], h, w)
        if status:
            explained[:status[1], :] = True
        if nav:
            explained[nav[0]:, :] = True
        ground = np.abs(sides[1].med - bg).max(axis=1)
        dev = np.abs(fo - bg).max(axis=2)
        left = (dev >= ground[:, None] + RESIDUAL_LEVELS) & ~explained
        ys, xs = np.nonzero(left)
        if xs.size:
            area = [int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1]
            rows.append([label, "(residual)", "residual", "MISS", "-", fmt(area), "-", f"{xs.size} px of funeOS ink outside every shown funeplayer region"])
        else:
            rows.append([label, "(residual)", "residual", "ok", "-", "-", "-", "no funeOS ink outside the shown funeplayer regions"])
    return label


def allowlist(path):
    out = {}
    if path is None:
        return out
    for line in Path(path).read_text().splitlines():
        parts = line.split(maxsplit=1)
        if len(parts) == 2:
            out[parts[0]] = parts[1]
    return out


def main():
    p = argparse.ArgumentParser(description="ink band check of funeplayer sheet tiles against funeOS shots")
    p.add_argument("--ref", required=True, help="funeOS shots, <ref>/<board>/<kind>.png")
    p.add_argument("--tokens", required=True, help="funeOS tokens.txt with color.bg")
    p.add_argument("--report", required=True, help="report file to write")
    p.add_argument("--allow", help="lines of <board>/<kind> <reason> for pairs with no gated region and tiles with no reference")
    p.add_argument("sheets", nargs="+", help="directories searched for <tile>.regions.json")
    args = p.parse_args()
    bg = token_color(args.tokens, "color.bg")
    allowed = allowlist(args.allow)
    rows = []
    noref = []
    pairs = []
    residuals = []
    unpeaked = []
    for d in args.sheets:
        for f in sorted(Path(d).rglob("*.regions.json")):
            label = check(f, args.ref, bg, rows, noref, residuals, unpeaked)
            if label:
                pairs.append(label)
    head = ["pair", "region", "kind", "result", "funeplayer x0,y0,x1,y1", "funeOS x0,y0,x1,y1", "d x0,y0,x1,y1", "note"]
    table = [head] + rows
    widths = [max(len(str(r[i])) for r in table) for i in range(len(head))]
    lines = ["  ".join(str(c).ljust(widths[i]) for i, c in enumerate(r)).rstrip() for r in table]
    gated_pairs = {r[0] for r in rows if r[3] in ("ok", "MISS") and r[2] != "residual"}
    ungated = [x for x in pairs if x not in gated_pairs]
    listed = [f"residual pass per pair: {len(residuals)}"] + residuals
    listed += [f"gated on edges only, the peak off: {len(unpeaked)}"] + unpeaked
    unlisted = 0
    for title, names in (("pairs with no gated region", ungated), ("tiles with no reference", noref)):
        listed.append(f"{title}: {len(names)}")
        for n in names:
            reason = allowed.get(n)
            if reason is None:
                unlisted += 1
            listed.append(f"  {n}: {reason if reason else 'NOT ALLOWLISTED'}")
    stale = [n for n in allowed if n not in ungated and n not in noref]
    if stale:
        listed.append(f"allowlist entries that match nothing: {len(stale)}")
        listed += [f"  {n}" for n in stale]
    masked = sum(1 for r in rows if r[3] in ("ok", "MISS", "text") and "ground masks" in r[7])
    misses = sum(1 for r in rows if r[3] == "MISS")
    gated = sum(1 for r in rows if r[3] in ("ok", "MISS"))
    texts = sum(1 for r in rows if r[3] == "text")
    excluded = sum(1 for r in rows if r[3] == "excluded")
    summary = f"inkband: {len(pairs)} pairs, {gated} gated regions, {misses} misses, {texts} text regions reported, {excluded} excluded, {masked} measured with ground masked rows, {len(unpeaked)} gated on edges only, {unlisted} unlisted, {len(stale)} stale allowlist entries"
    if not pairs:
        summary += f"; no tile has a reference under {args.ref}"
    out = "\n".join(lines + [""] + listed + ["", summary]) + "\n"
    Path(args.report).parent.mkdir(parents=True, exist_ok=True)
    Path(args.report).write_text(out)
    sys.stdout.write(out)
    return 1 if misses or unlisted or stale or not pairs else 0


if __name__ == "__main__":
    sys.exit(main())
