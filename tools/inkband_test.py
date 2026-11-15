#!/usr/bin/env python3
import re
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
FIX = TOOLS / "fixtures" / "inkband"
PRESS = "Press, glow, pressed steps"


def run(cases, allow, k3=()):
    with tempfile.TemporaryDirectory() as d:
        allow_path = Path(d) / "allow.txt"
        allow_path.write_text("".join(line + "\n" for line in allow))
        cmd = [sys.executable, str(TOOLS / "inkband.py"), "--ref", str(FIX / "ref"), "--tokens", str(FIX / "tokens.txt"), "--allow", str(allow_path), "--report", str(Path(d) / "report.txt")]
        if k3 is not None:
            k3_path = Path(d) / "k3.txt"
            k3_path.write_text("".join(line + "\n" for line in k3))
            cmd += ["--k3", str(k3_path)]
        dirs = [cases] if isinstance(cases, str) else cases
        p = subprocess.run(cmd + [str(FIX / c) for c in dirs], capture_output=True, text=True)
        return p.returncode, p.stdout + p.stderr


def misses(out):
    return int(re.search(r"^inkband: .*?, (\d+) misses,", out, re.M).group(1))


def opened(out):
    m = re.search(r"^open: (\d+) regions in (\d+) pairs$", out, re.M)
    return int(m.group(1)), int(m.group(2))


def result(out, pair, region):
    for line in out.splitlines():
        cols = line.split()
        if len(cols) >= 4 and cols[0] == pair and cols[1] == region:
            return cols[3]
    return None


class OpenLines(unittest.TestCase):
    def test_a_miss_in_a_listed_region_passes_and_shows_on_the_open_line(self):
        rc, out = run("press", [f"fx/press open {PRESS} stack2 row1.tile,row4.tile the press form waits for its stack"])
        self.assertEqual(rc, 0, out)
        self.assertEqual(opened(out), (2, 1), out)
        self.assertIn(f"  fx/press {PRESS} stack2: row1.tile, row4.tile", out)
        self.assertEqual(result(out, "fx/press", "row1.tile"), "open", out)

    def test_an_open_pair_whose_excused_rows_are_its_only_misses_exits_0_with_no_miss_counted(self):
        rc, out = run("press", [f"fx/press open {PRESS} stack2 row1.tile,row4.tile the press form waits for its stack"])
        self.assertEqual(rc, 0, out)
        self.assertEqual(misses(out), 0, out)
        self.assertEqual(result(out, "fx/press", "row2.tile"), "ok", out)

    def test_a_miss_in_an_unlisted_region_of_the_pair_fails(self):
        rc, out = run("press", [f"fx/press open {PRESS} stack2 row1.tile the press form waits for its stack"])
        self.assertEqual(rc, 1, out)
        self.assertEqual(misses(out), 1, out)
        self.assertEqual(result(out, "fx/press", "row4.tile"), "MISS", out)
        self.assertEqual(opened(out), (1, 1), out)

    def test_a_listed_region_with_no_miss_makes_the_line_stale_and_names_that_entry(self):
        rc, out = run("press", [f"fx/press open {PRESS} stack2 row1.tile,row2.tile,row4.tile the press form waits for its stack"])
        self.assertEqual(rc, 1, out)
        self.assertEqual(misses(out), 0, out)
        self.assertIn(f"  fx/press open {PRESS} stack2: row2.tile matches no miss", out)
        self.assertNotIn("row1.tile matches no miss", out)

    def test_a_line_whose_stack_is_in_k3_is_stale(self):
        rc, out = run("press", [f"fx/press open {PRESS} stack2 row1.tile,row4.tile the press form waits for its stack"], k3=["stack1", "stack2"])
        self.assertEqual(rc, 1, out)
        self.assertIn(f"  fx/press open {PRESS} stack2: stack2 is in k3.txt", out)
        self.assertEqual(misses(out), 2, out)

    def test_a_line_whose_stack_is_not_in_k3_holds(self):
        rc, out = run("press", [f"fx/press open {PRESS} stack2 row1.tile,row4.tile the press form waits for its stack"], k3=["stack1"])
        self.assertEqual(rc, 0, out)

    def test_no_open_line_is_taken_without_k3(self):
        rc, out = run("press", [f"fx/press open {PRESS} stack2 row1.tile,row4.tile the press form waits for its stack"], k3=None)
        self.assertEqual(rc, 1, out)
        self.assertIn("no open line is taken without --k3", out)
        self.assertEqual(misses(out), 2, out)

    def test_a_prefix_excuses_its_rows_and_not_a_longer_number(self):
        rc, out = run("prefix", [f"fx/prefix open {PRESS} stack2 row3.* the press form waits for its stack"])
        self.assertEqual(rc, 1, out)
        self.assertEqual(result(out, "fx/prefix", "row3.glow"), "open", out)
        self.assertEqual(result(out, "fx/prefix", "row30.title"), "MISS", out)
        self.assertEqual(misses(out), 1, out)

    def test_two_lines_on_one_pair_each_excuse_their_own_regions(self):
        rc, out = run("toast", [
            "fx/toast-500-press open Toast stack2 toast.* the toast form waits for its stack",
            f"fx/toast-500-press open {PRESS} stack2 row2.* the press form waits for its stack",
        ])
        self.assertEqual(rc, 0, out)
        self.assertEqual(opened(out), (3, 1), out)
        self.assertIn("  fx/toast-500-press Toast stack2: toast.strip, toast.text", out)
        self.assertIn(f"  fx/toast-500-press {PRESS} stack2: row2.tile", out)

    def test_the_press_line_goes_stale_when_its_misses_are_fixed_and_the_toast_line_holds(self):
        rc, out = run("toastfixed", [
            "fx/toast-500-press open Toast stack2 toast.* the toast form waits for its stack",
            f"fx/toast-500-press open {PRESS} stack2 row2.* the press form waits for its stack",
        ])
        self.assertEqual(rc, 1, out)
        self.assertEqual(misses(out), 0, out)
        self.assertEqual(opened(out), (2, 1), out)
        self.assertIn(f"  fx/toast-500-press open {PRESS} stack2: row2.* matches no miss", out)
        self.assertNotIn("open Toast stack2: toast.* matches no miss", out)

    def test_residual_as_an_entry_fails_the_line(self):
        rc, out = run("residual", [f"fx/resid open {PRESS} stack2 (residual) the press form waits for its stack"])
        self.assertEqual(rc, 1, out)
        self.assertIn(f"  fx/resid open {PRESS} stack2: (residual) may not be listed", out)
        self.assertEqual(result(out, "fx/resid", "(residual)"), "MISS", out)

    def test_a_star_entry_takes_in_residual_and_fails_the_line(self):
        rc, out = run("residual", [f"fx/resid open {PRESS} stack2 * the press form waits for its stack"])
        self.assertEqual(rc, 1, out)
        self.assertIn("(residual) may not be listed", out)

    def test_a_line_excuses_only_the_misses_of_its_own_pair(self):
        rc, out = run(["press", "prefix"], [f"fx/press open {PRESS} stack2 row* the press form waits for its stack"])
        self.assertEqual(rc, 1, out)
        self.assertEqual(result(out, "fx/press", "row1.tile"), "open", out)
        self.assertEqual(result(out, "fx/prefix", "row3.glow"), "MISS", out)
        self.assertEqual(result(out, "fx/prefix", "row30.title"), "MISS", out)
        self.assertEqual(misses(out), 2, out)
        self.assertEqual(opened(out), (2, 1), out)

    def test_the_size_row_as_an_entry_fails_the_line(self):
        rc, out = run("size", [f"fx/size open {PRESS} stack2 (tile) the press form waits for its stack"])
        self.assertEqual(rc, 1, out)
        self.assertIn(f"  fx/size open {PRESS} stack2: (tile) may not be listed", out)
        self.assertEqual(result(out, "fx/size", "(tile)"), "MISS", out)
        self.assertEqual(misses(out), 1, out)

    def test_an_empty_entry_is_refused_and_the_misses_kept(self):
        rc, out = run("press", [f"fx/press open {PRESS} stack2 row1.tile,,row4.tile the press form waits for its stack"])
        self.assertEqual(rc, 1, out)
        self.assertIn(f"  fx/press open {PRESS} stack2: an empty region entry", out)
        self.assertEqual(misses(out), 2, out)

    def test_a_line_with_no_stack_is_refused(self):
        rc, out = run("press", [f"fx/press open {PRESS} row1.tile,row4.tile no stack named"])
        self.assertEqual(rc, 1, out)
        self.assertIn("not <board>/<kind> open <part> stack<N> <regions> <reason>", out)
        self.assertEqual(misses(out), 2, out)


if __name__ == "__main__":
    unittest.main()
