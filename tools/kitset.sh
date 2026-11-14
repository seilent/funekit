#!/usr/bin/env bash
set -euo pipefail

mode=list
if [ "${1:-}" = "--set" ]; then
    mode=set
    shift
fi
if [ $# -ne 1 ] || [ ! -d "$1" ]; then
    echo "usage: tools/kitset.sh [--set] <dir>" >&2
    exit 2
fi

listing() {
    cd "$1"
    find . \( -name .git -o -name build -o -name .gradle -o -name .kotlin -o -name local.properties \) -prune \
        -o -type f ! -path ./.gitmodules -print0 |
        LC_ALL=C sort -z |
        while IFS= read -r -d '' f; do printf '%s\0' "${f#./}"; done |
        xargs -0 -r sha256sum --
}

if [ "$mode" = set ]; then
    listing "$1" | sha256sum | cut -d' ' -f1
else
    listing "$1"
fi
