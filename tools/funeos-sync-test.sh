#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "${tmp:?}"' EXIT

fail() { echo "funeos-sync-test: $*" >&2; exit 1; }

src=$tmp/funeos
mkdir -p "$src/player/lib"
cp -r "$root/tokens/funeos/player" "$src/"
cp "$src/player/ui/tokens-v1.txt" "$src/player/ui/tokens-pb.txt"
printf 'lrc\n' > "$src/player/lib/lrc.cpp"
printf 'lrc h\n' > "$src/player/lib/lrc.h"
printf 'util\n' > "$src/player/lib/util.cpp"
git -C "$src" init -q
git -C "$src" -c user.name=t -c user.email=t@t add -A
git -C "$src" -c user.name=t -c user.email=t@t commit -qm one

kit=$tmp/kit
mkdir -p "$kit/tools" "$kit/core/src/test/resources/lrc"
cp "$root/tools/funeos-sync.sh" "$kit/tools/"
tar -C "$root" --exclude=build -cf - tokens | tar -x -C "$kit"
(cd "$src" && sha256sum player/ui/tokens.txt player/ui/tokens-v1.txt player/ui/tokens-r1.txt player/ui/tokens-a105.txt \
    player/ui/tokens-pb.txt player/ui/pagemove.cpp player/ui/pagemove.h player/gfx/glyph.cpp) | sed 's/  / /' > "$tmp/audit"
(cd "$src" && sha256sum player/lib/lrc.cpp player/lib/lrc.h player/lib/util.cpp) | sed 's/  / /' > "$kit/core/src/test/resources/lrc/SOURCE"
echo "from funeOS test" >> "$kit/core/src/test/resources/lrc/SOURCE"

run() { (cd "$kit" && FUNEOS=$src bash tools/funeos-sync.sh "$@") > "$tmp/out" 2>&1; }
pins() { (cd "$kit" && sha256sum tokens/funeos/SOURCE tokens/fit.txt tokens/funeos/player/ui/*); }

run ui HEAD --audit "$tmp/audit" --name "funeOS test" || fail "a clean content exits $?: $(cat "$tmp/out")"
grep -q "clean against" "$tmp/out" || fail "a clean run does not say clean"

before=$(pins)
grep -v "tokens-pb.txt" "$tmp/audit" > "$tmp/short"
if run ui HEAD --audit "$tmp/short" --name "funeOS test" --accept; then fail "a missing audit line exits 0"; else rc=$?; fi
[ "$rc" -eq 1 ] || fail "a missing audit line exits $rc, not 1"
grep -q "player/ui/tokens-pb.txt at .* is not in the clean audit" "$tmp/out" || fail "a missing audit line is not named"
[ "$(pins)" = "$before" ] || fail "a missing audit line wrote the pins"

sed -i 's/^motion\.t2Peek 200 /motion.t2Peek 201 /' "$src/player/ui/tokens-a105.txt"
git -C "$src" -c user.name=t -c user.email=t@t commit -qam two
(cd "$src" && sha256sum player/ui/tokens-a105.txt) | sed 's/  / /' >> "$tmp/audit"
if run ui HEAD --audit "$tmp/audit" --name "funeOS test"; then fail "a changed value exits 0 without --accept"; else rc=$?; fi
[ "$rc" -eq 1 ] || fail "a changed value exits $rc, not 1"
[ "$(pins)" = "$before" ] || fail "a changed value wrote the pins without --accept"
run ui HEAD --audit "$tmp/audit" --name "funeOS test" --accept || fail "--accept exits $?: $(cat "$tmp/out")"
grep -q "^motion.t2Peek 201 " "$kit/tokens/funeos/player/ui/tokens-a105.txt" || fail "--accept did not write the file"
head -1 "$kit/tokens/funeos/SOURCE" | grep -qx "from funeOS test" || fail "SOURCE does not name the release"
grep -q "^commit" "$kit/tokens/funeos/SOURCE" && fail "SOURCE took a commit line with no --origin"
grep -q "^motion.t2Peek " "$kit/tokens/fit.txt" || fail "the fit file did not follow the board change"
set=$( (cd "$kit/tokens/funeos" && find player -type f | LC_ALL=C sort | xargs sha256sum) | sha256sum | cut -d' ' -f1)
grep -qx "set $set" "$kit/tokens/funeos/SOURCE" || fail "SOURCE set is not the vendored set hash"
for odd in 1. .5; do
    sed -i "s/^motion\.t2Peek [^ ]* /motion.t2Peek $odd /" "$src/player/ui/tokens-a105.txt"
    git -C "$src" -c user.name=t -c user.email=t@t commit -qam "odd $odd"
    (cd "$src" && sha256sum player/ui/tokens-a105.txt) | sed 's/  / /' >> "$tmp/audit"
    run ui HEAD --audit "$tmp/audit" --name "funeOS test" --accept || fail "--accept of $odd exits $?: $(cat "$tmp/out")"
    grep -qx "motion.t2Peek step" "$kit/tokens/fit.txt" || fail "$odd is not a step value in the fit file"
done
git init -q --bare "$tmp/origin.git"
git -C "$src" remote add origin "$tmp/origin.git"
before=$(pins)
if run ui HEAD --audit "$tmp/audit" --name "funeOS test" --origin --accept; then fail "--origin with no origin branch exits 0"; else rc=$?; fi
[ "$rc" -eq 2 ] || fail "--origin with no origin branch exits $rc, not 2"
grep -q "is on no funeOS origin branch" "$tmp/out" || fail "--origin with no origin branch does not say so"
[ "$(pins)" = "$before" ] || fail "--origin with no origin branch wrote the pins"
git -C "$src" push -q origin HEAD:refs/heads/main
git -C "$src" fetch -q origin
run ui HEAD --audit "$tmp/audit" --name "funeOS test" --origin --accept || fail "--origin on a pushed commit exits $?: $(cat "$tmp/out")"
grep -qx "commit $(git -C "$src" rev-parse HEAD)" "$kit/tokens/funeos/SOURCE" || fail "SOURCE took no commit line with --origin"
git -C "$tmp/origin.git" update-ref -d refs/heads/main
if run ui HEAD --audit "$tmp/audit" --name "funeOS test" --origin; then fail "--origin passes from a stale remote tracking ref"; else rc=$?; fi
[ "$rc" -eq 2 ] || fail "--origin from a stale remote tracking ref exits $rc, not 2"

run lrc HEAD || fail "equal lrc sources exit $?: $(cat "$tmp/out")"
printf 'lrc two\n' > "$src/player/lib/lrc.cpp"
git -C "$src" -c user.name=t -c user.email=t@t commit -qam three
if run lrc HEAD; then fail "a changed lrc source exits 0"; else rc=$?; fi
[ "$rc" -eq 1 ] || fail "a changed lrc source exits $rc, not 1"
grep -q "player/lib/lrc.cpp at .* pins" "$tmp/out" || fail "a changed lrc source is not named"

echo "funeos-sync-test: ok"
