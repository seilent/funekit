#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"

usage() {
    echo "usage: tools/funeos-sync.sh ui <funeos-sha> --audit <file> [--accept]" >&2
    exit 2
}

[ $# -ge 4 ] && [ $# -le 5 ] || usage
case $1 in
    ui) ;;
    *) echo "funeos-sync: unknown set $1" >&2; exit 2 ;;
esac
[ "$3" = --audit ] || usage
audited=$4
[ -f "$audited" ] || { echo "funeos-sync: no audit file $audited" >&2; exit 2; }
accept=0
if [ $# -eq 5 ]; then
    [ "$5" = --accept ] || usage
    accept=1
fi

[ -n "${FUNEOS:-}" ] || { echo "funeos-sync: set FUNEOS to the funeOS checkout" >&2; exit 2; }
funeos=$FUNEOS
full=$(git -C "$funeos" rev-parse --verify "$2^{commit}")

tmp=$(mktemp -d)
trap 'rm -rf "${tmp:?}"' EXIT

tokens="tokens.txt tokens-v1.txt tokens-r1.txt tokens-a105.txt"
files=(player/ui/tokens.txt player/ui/tokens-v1.txt player/ui/tokens-r1.txt player/ui/tokens-a105.txt player/gfx/glyph.cpp)
git -C "$funeos" archive "$full" "${files[@]}" | tar -x -C "$tmp"

classes() {
    paste -d' ' "$1/player/ui/tokens-v1.txt" "$1/player/ui/tokens-r1.txt" "$1/player/ui/tokens-a105.txt" | awk '
        function half(s,   d) { d = index(s, ".") ? length(s) - index(s, ".") : 0; return 5 / (10 ^ (d + 1)) }
        function num(s) { return s ~ /^-?[0-9]+(\.[0-9]+)?$/ }
        function meet(n,   i, lo, hi) {
            lo = L[1]; hi = U[1]
            for (i = 2; i <= n; i++) { if (L[i] > lo) lo = L[i]; if (U[i] < hi) hi = U[i] }
            return lo <= hi
        }
        function anchored(from,   i, n) {
            n = 0
            for (i = from; i <= 3; i++) { n++; L[n] = H[i] - X[i] - E[i]; U[n] = H[i] - X[i] + E[i] }
            return meet(n)
        }
        function ratio(by,   i) {
            for (i = 1; i <= 3; i++) { L[i] = (X[i] - E[i]) / by[i]; U[i] = (X[i] + E[i]) / by[i] }
            return meet(3)
        }
        BEGIN {
            split("motion.volumeTiltAngle motion.volumeTiltDepth qpTiltAngle motion.contentHubRows motion.contentPageRows motion.npPace", b, " ")
            for (i in b) budget[b[i]] = 1
        }
        { name[NR] = $1; v1[NR] = $2; r1[NR] = $5; a1[NR] = $8; unit[NR] = $3 }
        $1 == "canvasH" { H[1] = $2; H[2] = $5; H[3] = $8 }
        $1 == "u" { P[1] = $2; P[2] = $5; P[3] = $8 }
        END {
            for (k = 1; k <= NR; k++) {
                if (v1[k] == r1[k] && v1[k] == a1[k]) continue
                n = name[k]
                if (n in budget) c = "budget"
                else if (n ~ /^motion\.slices/ || n == "motion.npSlices" || n == "saver.aheadMs") c = "x"
                else if (unit[k] == "count" || unit[k] == "mode" || !num(v1[k]) || !num(r1[k]) || !num(a1[k])) c = "step"
                else {
                    X[1] = v1[k]; X[2] = r1[k]; X[3] = a1[k]
                    E[1] = half(v1[k]); E[2] = half(r1[k]); E[3] = half(a1[k])
                    if (anchored(1)) c = "h"
                    else if (ratio(H)) c = "hr"
                    else if (ratio(P)) c = "ppu"
                    else { L[1] = X[2] - E[2]; U[1] = X[2] + E[2]; L[2] = X[3] - E[3]; U[2] = X[3] + E[3]
                        if (meet(2)) c = "v1c"; else if (anchored(2)) c = "v1h"; else c = "free" }
                }
                print n, c
            }
        }'
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
    diff -u "tokens/funeos/player/ui/$f" "$tmp/player/ui/$f" || true
done
diff -u tokens/funeos/player/gfx/glyph.cpp "$tmp/player/gfx/glyph.cpp" || true

classes "$tmp" > "$tmp/fit.txt"
if ! cmp -s tokens/fit.txt "$tmp/fit.txt"; then
    echo "funeos-sync: numeric classes changed:"
    diff tokens/fit.txt "$tmp/fit.txt" || true
    stop=1
fi

audit=0
for f in "${files[@]}"; do
    s=$(sha256sum "$tmp/$f" | cut -d' ' -f1)
    name=${f##*/}
    if ! grep -Eq "^$s[[:space:]]+${name//./\\.}[[:space:]]*\$" "$audited"; then
        echo "funeos-sync: $f at $full ($s) is not in the clean audit $audited"
        audit=1
    fi
done

if [ "$audit" -ne 0 ]; then
    echo "funeos-sync: stopped, pins unchanged" >&2
    exit 1
fi
if [ "$accept" -eq 0 ]; then
    [ "$stop" -eq 0 ] && echo "funeos-sync: clean against $full; --accept writes the pins"
    [ "$stop" -ne 0 ] && echo "funeos-sync: stopped, pins unchanged; review, then --accept" >&2
    exit "$stop"
fi

for f in "${files[@]}"; do cp "$tmp/$f" "tokens/funeos/$f"; done
(cd tokens/funeos && find player -type f | LC_ALL=C sort | xargs sha256sum) | sha256sum | cut -d' ' -f1 > tokens/funeos/SET.sha256
echo "$full" > tokens/funeos/FUNEOS_FROM
cp "$tmp/fit.txt" tokens/fit.txt
echo "funeos-sync: pinned $full, set $(cat tokens/funeos/SET.sha256)"
