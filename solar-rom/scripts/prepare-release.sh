#!/usr/bin/env bash
# Emit release tag and metadata for CI from branch + app/build.gradle.
# main: stable tag v{versionName}; nightly: tag matches versionName in app/build.gradle
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/solar-repo.sh"
GRADLE="$REPO_ROOT/app/build.gradle"

die() {
    echo "error: $*" >&2
    exit 1
}

apply_gradle_version() {
    local name="$1" code="$2"
    python3 - "$GRADLE" "$name" "$code" <<'PY'
import re, sys
path, name, code = sys.argv[1], sys.argv[2], sys.argv[3]
text = open(path, encoding="utf-8").read()
text = re.sub(r'versionName\s+"[^"]+"', f'versionName "{name}"', text, count=1)
text = re.sub(r'versionCode\s+\d+', f"versionCode {code}", text, count=1)
open(path, "w", encoding="utf-8").write(text)
PY
}

[ -f "$GRADLE" ] || die "missing $GRADLE"

BRANCH="${GITHUB_REF_NAME:-$(git -C "$REPO_ROOT" branch --show-current 2>/dev/null || echo "")}"
BUILD_NUM="${GITHUB_RUN_NUMBER:-}"
SHORT_SHA="$(git -C "$REPO_ROOT" rev-parse --short HEAD 2>/dev/null || echo unknown)"

if [ "$BRANCH" = "nightly" ]; then
    CHANNEL="nightly"
    VERSION_NAME="$(sed -n 's/.*versionName "\([^"]*\)".*/\1/p' "$GRADLE" | head -1)"
    VERSION_CODE="$(sed -n 's/.*versionCode \([0-9]*\).*/\1/p' "$GRADLE" | head -1)"
    [[ "$VERSION_NAME" == nightly-* ]] || die "nightly builds require versionName nightly-N in app/build.gradle"
    [ -n "$VERSION_CODE" ] || die "could not read versionCode"
    TAG="$VERSION_NAME"
elif [ "$BRANCH" = "main" ]; then
    CHANNEL="stable"
    VERSION_NAME="$(sed -n 's/.*versionName "\([^"]*\)".*/\1/p' "$GRADLE" | head -1)"
    VERSION_CODE="$(sed -n 's/.*versionCode \([0-9]*\).*/\1/p' "$GRADLE" | head -1)"
    TAG="v${VERSION_NAME}"
else
    die "releases only from main or nightly (branch: ${BRANCH:-unknown})"
fi

[ -n "$VERSION_NAME" ] || die "could not read versionName"
[ -n "$VERSION_CODE" ] || die "could not read versionCode"

python3 - <<PY
import json
import os

meta = {
    "tag": "${TAG}",
    "channel": "${CHANNEL}",
    "version_name": "${VERSION_NAME}",
    "version_code": int("${VERSION_CODE}"),
    "short_sha": "${SHORT_SHA}",
    "github_repo": "${SOLAR_GITHUB_REPO}",
}
with open("release.json", "w", encoding="utf-8") as handle:
    json.dump(meta, handle, indent=2)
print(json.dumps(meta))

github_output = os.environ.get("GITHUB_OUTPUT")
if github_output:
    with open(github_output, "a", encoding="utf-8") as handle:
        for key in ("tag", "channel", "version_name", "version_code", "short_sha"):
            handle.write(f"{key}={meta[key]}\n")
PY
