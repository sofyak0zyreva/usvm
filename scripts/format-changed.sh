#!/usr/bin/env bash
# Formats the Kotlin files changed on this branch (compared to main) with the project's
# detekt formatting rules (detekt/config.yml), the same rules as `./gradlew detekt --auto-correct`.
#
#   ./scripts/format-changed.sh           format the changed files
#   ./scripts/format-changed.sh --check   format, then fail if that changed anything (used by CI)
#
# BASE_REF (default origin/main) is the branch to compare against.
set -euo pipefail

DETEKT_VERSION=1.23.5
BASE_REF=${BASE_REF:-origin/main}
CHECK=false
[ "${1:-}" = "--check" ] && CHECK=true

cd "$(git rev-parse --show-toplevel)"

TOOLS=build/detekt-cli
CLI="$TOOLS/detekt-cli-$DETEKT_VERSION/bin/detekt-cli"
PLUGIN="$TOOLS/detekt-formatting-$DETEKT_VERSION.jar"
if [ ! -x "$CLI" ] || [ ! -f "$PLUGIN" ]; then
  echo "Downloading detekt $DETEKT_VERSION..."
  mkdir -p "$TOOLS"
  RELEASES=https://github.com/detekt/detekt/releases/download/v$DETEKT_VERSION
  curl -sSfL -o "$TOOLS/detekt-cli.zip" "$RELEASES/detekt-cli-$DETEKT_VERSION.zip"
  unzip -qo "$TOOLS/detekt-cli.zip" -d "$TOOLS"
  curl -sSfL -o "$PLUGIN" "$RELEASES/detekt-formatting-$DETEKT_VERSION.jar"
fi

# Changed = committed on this branch + uncommitted + new untracked files.
BASE=$(git merge-base "$BASE_REF" HEAD)
FILES=$(
  {
    git diff --name-only --diff-filter=ACMR "$BASE" -- '*.kt' '*.kts'
    git ls-files --others --exclude-standard -- '*.kt' '*.kts'
  } | grep -v -e '/build/' -e '/generated/' -e '/resources/' | sort -u || true
)
if [ -z "$FILES" ]; then
  echo "No changed Kotlin files."
  exit 0
fi
echo "Formatting $(echo "$FILES" | wc -l | tr -d ' ') changed file(s)"

# The formatter sometimes needs a second pass to settle, and detekt 1.23.5 occasionally
# crashes mid-run ("Stack should be empty"), writing nothing; so run until a pass succeeds twice.
LOG="$TOOLS/last-run.log"
ok=0
for attempt in 1 2 3 4 5; do
  "$CLI" --input "$(echo "$FILES" | paste -sd, -)" \
    --config detekt/config.yml --build-upon-default-config \
    --plugins "$PLUGIN" --auto-correct >"$LOG" 2>&1 || true
  if grep -q "led to an exception" "$LOG"; then
    echo "detekt crashed on attempt $attempt, retrying..."
    continue
  fi
  ok=$((ok + 1))
  [ "$ok" -eq 2 ] && break
done
if [ "$ok" -lt 2 ]; then
  echo "detekt kept crashing, see $LOG"
  exit 2
fi

if $CHECK; then
  # shellcheck disable=SC2086
  if ! git diff --exit-code -- $FILES; then
    echo "::error::These files are not formatted. Run ./scripts/format-changed.sh and commit the result."
    exit 1
  fi
  echo "All changed files are formatted."
else
  echo "Done. Review with: git diff"
fi
