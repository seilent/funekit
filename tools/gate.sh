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
        shots-cand)
            if [ -z "${FUNEOS_REF:-}" ]; then
                echo "gate: leg shots-cand needs FUNEOS_REF, the funeOS shots as <dir>/<board>/<kind>.png" >&2
                exit 2
            fi
            if [ -z "${SHOTS_CAND:-}" ] || ! compgen -G "$SHOTS_CAND/*.patch" > /dev/null; then
                echo "gate: leg shots-cand needs SHOTS_CAND, a dir of candidate commits as format-patch files in row order" >&2
                exit 2
            fi ;;
        probe-fp)
            for v in PROBE_FP PROBE_FP_KIT PROBE_FP_PATCH FUNEOS_REF; do
                if [ -z "${!v:-}" ]; then
                    echo "gate: leg probe-fp needs $v (PROBE_FP the funeplayer mirror with kit/ at the stack head, PROBE_FP_KIT the stack head commit, PROBE_FP_PATCH the lane patch, FUNEOS_REF the funeOS shots)" >&2
                    exit 2
                fi
            done
            if [ ! -f "$PROBE_FP_PATCH" ]; then
                echo "gate: leg probe-fp: no patch at $PROBE_FP_PATCH" >&2
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
    gradlew "$root" :kit:testDebugUnitTest --tests '*Sheet*' --rerun :screens:testDebugUnitTest --tests '*Sheet*' --rerun || rc=$?
    python3 tools/inkband.py --ref "$FUNEOS_REF" --tokens tokens/funeos/player/ui/tokens.txt --allow tools/inkband-allow.txt \
        --k3 k3.txt --report kit/build/sheets/inkband.txt kit/build/sheets screens/build/sheets || ink=$?
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

shots_cand_leg() {
    local dir=$tmp/cand
    mkdir "$dir"
    tar -C "$root" --exclude=build --exclude=.gradle --exclude=.kotlin -cf - . | tar -C "$dir" -xf -
    local p
    for p in "$SHOTS_CAND"/*.patch; do
        echo "shots-cand: apply $(basename "$p") $(sed -n '1s/^From \([0-9a-f]*\) .*/\1/p' "$p")"
        if ! patch -d "$dir" -p1 --forward --batch < "$p"; then
            echo "shots-cand: $(basename "$p") does not apply" >&2
            return 2
        fi
    done
    local rc=0
    gradlew "$dir" :kit:testDebugUnitTest --tests '*Sheet*' --rerun :screens:testDebugUnitTest --tests '*Sheet*' --rerun || rc=$?
    echo "shots-cand: render rc=$rc"
    mkdir -p "$root/kit/build/sheets-cand"
    python3 "$dir/tools/inkband.py" --ref "$FUNEOS_REF" --tokens "$dir/tokens/funeos/player/ui/tokens.txt" \
        --allow "$dir/tools/inkband-allow.txt" --k3 "$dir/k3.txt" --report "$root/kit/build/sheets-cand/inkband.txt" \
        "$dir/kit/build/sheets" "$dir/screens/build/sheets" || true
    return "$rc"
}

probe_fp_leg() {
    local dir=$PROBE_FP log=$tmp/probe-fp.log rc=0 r=0
    if [ ! -f "$dir/kit/tools/kitset.sh" ]; then
        echo "probe-fp: $dir/kit holds no kit" >&2
        return 1
    fi
    if ! patch -d "$dir" -p1 --forward --batch < "$PROBE_FP_PATCH"; then
        echo "probe-fp: the patch does not apply" >&2
        return 1
    fi
    printf 'commit %s\nset %s\n' "$PROBE_FP_KIT" "$(cd "$dir" && bash kit/tools/kitset.sh --set kit)" > "$dir/kit.pin"
    gradlew "$dir" -PnoLastFm :ui:compileDebugUnitTestKotlin :app:compileFdroidDebugUnitTestKotlin \
        :wire:compileDebugUnitTestKotlin :client:compileDebugUnitTestKotlin :engine:compileDebugUnitTestKotlin > "$log" 2>&1 || r=$?
    echo "probe-fp: compile rc=$r"
    if [ "$r" -ne 0 ]; then
        grep -m 20 '^e: \|error:' "$log" | sed 's/^/probe-fp: compile: /' || true
        tail -n 40 "$log"
        return 1
    fi
    local t task args=()
    declare -A by=()
    for t in ${PROBE_FP_TESTS:-}; do
        by[${t#*@}]+=" ${t%%@*}"
    done
    for task in "${!by[@]}"; do
        args+=("$task")
        for t in ${by[$task]}; do
            args+=(--tests "*$t*")
        done
    done
    if [ ${#args[@]} -gt 0 ]; then
        r=0
        gradlew "$dir" -PnoLastFm --continue "${args[@]}" > "$tmp/probe-fp-tests.log" 2>&1 || r=$?
        echo "probe-fp: suites rc=$r"
        if [ "$r" -ne 0 ]; then
            grep -m 20 'FAILED' "$tmp/probe-fp-tests.log" | sed 's/^/probe-fp: /' || true
        fi
        rc=$((r > rc ? r : rc))
    fi
    r=0
    env FUNEOS_REF="$FUNEOS_REF" ANDROID_HOME="$ANDROID_HOME" "$dir/tools/gate.sh" shots || r=$?
    echo "probe-fp: shots rc=$r"
    return $((r > rc ? r : rc))
}

worst=0
for leg in "$@"; do
    set +e
    (
        set -e
        "${leg//-/_}_leg"
    )
    rc=$?
    set -e
    echo "gate: leg $leg rc=$rc" >&2
    if [ "$rc" -gt "$worst" ]; then
        worst=$rc
    fi
done
exit "$worst"
