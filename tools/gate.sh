#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"

if [ $# -eq 0 ]; then
    echo "usage: tools/gate.sh <leg>..." >&2
    exit 2
fi

for leg in "$@"; do
    case $leg in
        gradle) ;;
        shots)
            if [ -z "${FUNEOS_REF:-}" ]; then
                echo "gate: leg shots needs FUNEOS_REF, the funeOS shots as <dir>/<board>/<kind>.png" >&2
                exit 2
            fi ;;
        probe)
            if [ -z "${PROBE_FP:-}" ] || [ -z "${PROBE_FC:-}" ]; then
                echo "gate: leg probe needs PROBE_FP and PROBE_FC, the app mirrors with kit/ and the probe patch" >&2
                exit 2
            fi ;;
        *) echo "gate: unknown leg $leg" >&2; exit 2 ;;
    esac
done

for v in ANDROID_HOME KIT_HOME KIT_TMP JAVA_HOME GRADLE_USER_HOME; do
    if [ -z "${!v:-}" ]; then
        echo "gate: set $v" >&2
        exit 2
    fi
done

if [ -e .git ]; then
    echo "gate: $root is a git checkout, run the gate in a mirror" >&2
    exit 2
fi

mkdir -p "$KIT_HOME" "$KIT_TMP"
tmp=$(mktemp -d "$KIT_TMP/gate.XXXX")
trap 'rm -rf "${tmp:?}"' EXIT

gradlew() {
    local dir=$1
    shift
    (
        cd "$dir"
        [ -e local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties
        env HOME="$KIT_HOME" ANDROID_USER_HOME="$KIT_HOME/.android" \
            JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$tmp -Duser.home=$KIT_HOME" \
            ./gradlew --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process \
            -Pkotlin.incremental=false -Pandroid.builder.sdkDownload=false \
            "-Dorg.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8 -XX:+UseParallelGC" "$@"
    )
}

gradle_leg() {
    gradlew "$root" --continue check
}

shots_leg() {
    local unit=0 rc=0 ink=0
    python3 tools/inkband_test.py || unit=$?
    gradlew "$root" :kit:testDebugUnitTest --tests '*Sheet*' --rerun || rc=$?
    python3 tools/inkband.py --ref "$FUNEOS_REF" --tokens tokens/funeos/player/ui/tokens.txt --allow tools/inkband-allow.txt \
        --k3 k3.txt --report kit/build/sheets/inkband.txt kit/build/sheets || ink=$?
    rc=$((rc > ink ? rc : ink))
    return $((rc > unit ? rc : unit))
}

probe_one() {
    local name=$1 dir=$2
    shift 2
    if [ ! -f "$dir/kit/tools/kitset.sh" ]; then
        echo "gate: probe $name: $dir/kit holds no kit" >&2
        return 1
    fi
    local log=$tmp/probe-$name.log rc=0
    gradlew "$dir" --info "$@" > "$log" 2>&1 || rc=$?
    echo "probe $name: rc=$rc"
    grep -m 5 -i 'KotlinCompileDaemon\|kotlin compiler execution strategy\|in-process' "$log" | sed "s/^/probe $name: /" || true
    grep -m 3 'sdk.dir\|ANDROID_HOME\|android-sdk' "$log" | sed "s/^/probe $name: /" || true
    if [ "$rc" -ne 0 ]; then
        tail -n 60 "$log"
    fi
    return "$rc"
}

probe_leg() {
    local rc=0 fp=0 fc=0 red=0
    probe_one funeplayer "$PROBE_FP" -PnoLastFm :ui:genRes :ui:compileDebugKotlin :app:compileFdroidDebugKotlin \
        :app:minifyFdroidBenchmarkWithR8 || fp=$?
    probe_one funecomp "$PROBE_FC" :state:compileKotlin :sim:compileKotlin :sim:compileTestKotlin \
        :app:compileDebugKotlin || fc=$?
    local readme=$PROBE_FC/kit/README.md
    cp "$readme" "$tmp/readme"
    printf 'x' >> "$readme"
    if gradlew "$PROBE_FC" :state:compileKotlin > "$tmp/red.log" 2>&1; then
        echo "probe red: checkKitPin passed on a changed kit/" >&2
        red=1
    elif ! grep -q 'checkKitPin: kit/ hashes to' "$tmp/red.log"; then
        echo "probe red: the build failed for another reason" >&2
        tail -n 40 "$tmp/red.log"
        red=1
    else
        echo "probe red: checkKitPin failed on one changed byte, as it must"
    fi
    cp "$tmp/readme" "$readme"
    for r in $fp $fc $red; do
        if [ "$r" -gt "$rc" ]; then
            rc=$r
        fi
    done
    return "$rc"
}

worst=0
for leg in "$@"; do
    set +e
    (
        set -e
        "${leg}_leg"
    )
    rc=$?
    set -e
    echo "gate: leg $leg rc=$rc" >&2
    if [ "$rc" -gt "$worst" ]; then
        worst=$rc
    fi
done
exit "$worst"
