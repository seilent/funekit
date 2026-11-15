#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"

usage() {
    echo "usage: tools/funeos-sync.sh ui <funeos-sha> --audit <file> --name <release name> [--origin] [--accept]" >&2
    echo "       tools/funeos-sync.sh lrc <funeos-sha> [--source <file>]" >&2
    exit 2
}

[ -n "${FUNEOS:-}" ] || { echo "funeos-sync: set FUNEOS to the funeOS checkout" >&2; exit 2; }
funeos=$FUNEOS
[ $# -ge 2 ] || usage
mode=$1
full=$(git -C "$funeos" rev-parse --verify "$2^{commit}") || { echo "funeos-sync: $2 is not a funeOS commit" >&2; exit 2; }
shift 2

tmp=$(mktemp -d)
trap 'rm -rf "${tmp:?}"' EXIT

sha() { sha256sum "$1" | cut -d' ' -f1; }

if [ "$mode" = lrc ]; then
    source=core/src/test/resources/lrc/SOURCE
    if [ $# -eq 2 ] && [ "$1" = --source ]; then source=$2; elif [ $# -ne 0 ]; then usage; fi
    [ -f "$source" ] || { echo "funeos-sync: no lrc source $source" >&2; exit 2; }
    lrc=(player/lib/lrc.cpp player/lib/lrc.h player/lib/util.cpp)
    git -C "$funeos" archive "$full" "${lrc[@]}" | tar -x -C "$tmp"
    differ=0
    for f in "${lrc[@]}"; do
        want=$(awk -v p="$f" '$1 == p && $2 ~ /^[0-9a-f]{64}$/ { print $2 } $2 == p && $1 ~ /^[0-9a-f]{64}$/ { print $1 }' "$source")
        got=$(sha "$tmp/$f")
        if [ -z "$want" ]; then
            echo "funeos-sync: $source has no line for $f"
            differ=1
        elif [ "$want" != "$got" ]; then
            echo "funeos-sync: $f at $full is $got, $source pins $want"
            differ=1
        fi
    done
    loose=$(awk '$1 ~ /^[0-9a-f]{64}$/ { print $2 } $2 ~ /^[0-9a-f]{64}$/ { print $1 }' "$source" | grep -vxF -e "${lrc[0]}" -e "${lrc[1]}" -e "${lrc[2]}" || true)
    if [ -n "$loose" ]; then
        echo "funeos-sync: $source lists files the lrc mode does not read: $loose"
        differ=1
    fi
    [ "$differ" -eq 0 ] && echo "funeos-sync: lrc sources at $full equal $source"
    exit "$differ"
fi

[ "$mode" = ui ] || { echo "funeos-sync: unknown set $mode" >&2; exit 2; }
audited=
name=
origin=0
accept=0
while [ $# -gt 0 ]; do
    case $1 in
        --audit) [ $# -ge 2 ] || usage; audited=$2; shift 2 ;;
        --name) [ $# -ge 2 ] || usage; name=$2; shift 2 ;;
        --origin) origin=1; shift ;;
        --accept) accept=1; shift ;;
        *) usage ;;
    esac
done
[ -n "$audited" ] && [ -n "$name" ] || usage
[ -f "$audited" ] || { echo "funeos-sync: no audit file $audited" >&2; exit 2; }
if [ "$origin" -eq 1 ]; then
    onorigin=0
    while read -r tip _; do
        if git -C "$funeos" merge-base --is-ancestor "$full" "$tip" 2>/dev/null; then onorigin=1; fi
    done < <(git -C "$funeos" ls-remote --heads origin 2>/dev/null)
    if [ "$onorigin" -ne 1 ]; then
        echo "funeos-sync: $full is on no funeOS origin branch, so SOURCE takes no commit line" >&2
        exit 2
    fi
fi

tokens="tokens.txt tokens-v1.txt tokens-r1.txt tokens-a105.txt"
vendored=(player/gfx/glyph.cpp player/ui/pagemove.cpp player/ui/pagemove.h player/ui/tokens-a105.txt player/ui/tokens-r1.txt
    player/ui/tokens-v1.txt player/ui/tokens.txt)
audit=("${vendored[@]}" player/ui/tokens-pb.txt)
git -C "$funeos" archive "$full" "${audit[@]}" | tar -x -C "$tmp"

stop=0
for f in "${audit[@]}"; do
    s=$(sha "$tmp/$f")
    if ! grep -Eq "^$s[[:space:]]+${f//./\\.}[[:space:]]*\$" "$audited"; then
        echo "funeos-sync: $f at $full ($s) is not in the clean audit $audited"
        stop=2
    fi
done
if [ "$stop" -eq 2 ]; then
    echo "funeos-sync: stopped, pins unchanged" >&2
    exit 1
fi

classes() {
    python3 - "$1/player/ui" <<'PY'
import re
import sys
from decimal import Decimal, getcontext
getcontext().prec = 16
d = sys.argv[1]
each = [[l.split(" ") for l in open(f"{d}/{f}").read().splitlines() if l.strip()] for f in ("tokens-v1.txt", "tokens-r1.txt", "tokens-a105.txt")]
val = [{r[0]: r[1] for r in e} for e in each]
hs = [Decimal(v["canvasH"]) for v in val]
ps = [Decimal(v["u"]) for v in val]
def num(s):
    return re.fullmatch(r"-?[0-9]+(\.[0-9]+)?", s) is not None
def half(s):
    return Decimal(5).scaleb(-((len(s.split(".", 1)[1]) if "." in s else 0) + 1))
def meet(iv):
    return max(a for a, _ in iv) <= min(b for _, b in iv)
for r in each[0]:
    n, unit = r[0], r[2]
    vs = [v[n] for v in val]
    if len(set(vs)) == 1:
        continue
    if n.startswith("motion.slices") or n in ("motion.npSlices", "saver.aheadMs"):
        c = "x"
    elif unit in ("count", "mode") or not all(num(x) for x in vs):
        c = "step"
    elif n == "minTextPx":
        c = "ppuTop"
    else:
        lo = [Decimal(x) - half(x) for x in vs]
        hi = [Decimal(x) + half(x) for x in vs]
        anch = lambda ix: meet([(hs[i] - hi[i], hs[i] - lo[i]) for i in ix])
        ratio = lambda by: meet([(lo[i] / by[i], hi[i] / by[i]) for i in range(3)])
        if anch([0, 1, 2]): c = "h"
        elif ratio(hs): c = "hr"
        elif ratio(ps): c = "ppu"
        elif meet([(lo[i], hi[i]) for i in (1, 2)]): c = "v1c"
        elif anch([1, 2]): c = "v1h"
        else: c = "free"
    print(n, c)
PY
}

stop=0
for f in $tokens; do
    awk '{ print $1 }' "tokens/funeos/player/ui/$f" > "$tmp/old.names"
    awk '{ print $1 }' "$tmp/player/ui/$f" > "$tmp/new.names"
    if ! cmp -s "$tmp/old.names" "$tmp/new.names"; then
        echo "funeos-sync: $f adds, removes or moves tokens:"
        diff "$tmp/old.names" "$tmp/new.names" || true
        stop=1
    fi
done
for f in "${vendored[@]}"; do
    if ! cmp -s "tokens/funeos/$f" "$tmp/$f"; then
        diff -u "tokens/funeos/$f" "$tmp/$f" || true
        stop=1
    fi
done

classes "$tmp" > "$tmp/fit.txt"
if ! cmp -s tokens/fit.txt "$tmp/fit.txt"; then
    echo "funeos-sync: numeric classes changed:"
    diff tokens/fit.txt "$tmp/fit.txt" || true
    stop=1
fi

if [ "$accept" -eq 0 ]; then
    [ "$stop" -eq 0 ] && echo "funeos-sync: clean against $full; --accept writes the pins"
    [ "$stop" -ne 0 ] && echo "funeos-sync: stopped, pins unchanged; review, then --accept" >&2
    exit "$stop"
fi

for f in "${vendored[@]}"; do cp "$tmp/$f" "tokens/funeos/$f"; done
set=$( (cd tokens/funeos && find player -type f | LC_ALL=C sort | xargs sha256sum) | sha256sum | cut -d' ' -f1)
{
    echo "from $name"
    [ "$origin" -eq 1 ] && echo "commit $full"
    (cd tokens/funeos && find player -type f | LC_ALL=C sort | xargs sha256sum) | sed 's/  / /'
    echo "set $set"
} > tokens/funeos/SOURCE
cp "$tmp/fit.txt" tokens/fit.txt
echo "funeos-sync: pinned $name, set $set"
