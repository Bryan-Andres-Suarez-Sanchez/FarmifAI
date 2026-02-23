#!/usr/bin/env bash
set -euo pipefail

BUILD_TYPE="${1:-debug}"
REMOTE="${2:-origin}"
BRANCH="${3:-apk-builds}"
APK_PATH_OVERRIDE="${4:-}"

if [[ "$BUILD_TYPE" != "debug" && "$BUILD_TYPE" != "release" ]]; then
  echo "Uso: $0 [debug|release] [remote] [branch] [apk_path]"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TEMP_WORKTREE=""

cleanup() {
  if [[ -n "$TEMP_WORKTREE" && -d "$TEMP_WORKTREE" ]]; then
    git -C "$REPO_ROOT" worktree remove "$TEMP_WORKTREE" --force >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

pushd "$REPO_ROOT" >/dev/null

if [[ -n "$APK_PATH_OVERRIDE" ]]; then
  if [[ ! -f "$APK_PATH_OVERRIDE" ]]; then
    echo "Error: no existe APK en la ruta indicada: $APK_PATH_OVERRIDE"
    exit 2
  fi
  APK_PATH="$APK_PATH_OVERRIDE"
else
  if [[ "$BUILD_TYPE" == "release" ]]; then
    TASK=":app:assembleRelease"
    APK_DIR="$REPO_ROOT/app/build/outputs/apk/release"
  else
    TASK=":app:assembleDebug"
    APK_DIR="$REPO_ROOT/app/build/outputs/apk/debug"
  fi

  echo "Compilando APK ($BUILD_TYPE)..."
  ./gradlew "$TASK" --no-daemon

  APK_PATH="$(ls -t "$APK_DIR"/*.apk 2>/dev/null | head -n1 || true)"
  if [[ -z "$APK_PATH" ]]; then
    echo "Error: no se encontro APK en $APK_DIR"
    exit 2
  fi
fi

SHORT_SHA="$(git rev-parse --short HEAD)"
SOURCE_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
TIMESTAMP="$(date -u +%Y%m%d-%H%M%S)"
BASE_NAME="FarmifAI-${BUILD_TYPE}-${TIMESTAMP}-${SHORT_SHA}.apk"

REPO_URL="$(git remote get-url "$REMOTE")"
TEMP_WORKTREE="${TMPDIR:-/tmp}/farmifai-apk-publish-$(date +%s)-$$"

if git show-ref --verify --quiet "refs/heads/$BRANCH"; then
  git worktree add "$TEMP_WORKTREE" "$BRANCH"
elif git ls-remote --exit-code --heads "$REMOTE" "$BRANCH" >/dev/null 2>&1; then
  git worktree add -b "$BRANCH" "$TEMP_WORKTREE" "$REMOTE/$BRANCH"
else
  git worktree add --detach "$TEMP_WORKTREE" HEAD
  pushd "$TEMP_WORKTREE" >/dev/null
  git checkout --orphan "$BRANCH"
  git rm -rf . >/dev/null 2>&1 || true
  find . -mindepth 1 -maxdepth 1 ! -name ".git" -exec rm -rf {} +
  popd >/dev/null
fi

pushd "$TEMP_WORKTREE" >/dev/null

ARTIFACT_DIR="$TEMP_WORKTREE/apk-artifacts/$BUILD_TYPE"
mkdir -p "$ARTIFACT_DIR"

cp "$APK_PATH" "$ARTIFACT_DIR/$BASE_NAME"
cp "$APK_PATH" "$ARTIFACT_DIR/latest-$BUILD_TYPE.apk"

cat > "$ARTIFACT_DIR/latest-$BUILD_TYPE.json" <<EOF
{
  "file": "latest-$BUILD_TYPE.apk",
  "archived_file": "$BASE_NAME",
  "source_commit": "$SHORT_SHA",
  "source_branch": "$SOURCE_BRANCH",
  "built_at_utc": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
EOF

git add "apk-artifacts/$BUILD_TYPE"
if ! git diff --cached --quiet; then
  git commit -m "apk($BUILD_TYPE): $BASE_NAME"
  git push "$REMOTE" "HEAD:$BRANCH"
else
  echo "No hay cambios para publicar."
fi

if [[ "$REPO_URL" =~ ^git@github\.com:(.+)\.git$ ]]; then
  REPO_HTTP="https://github.com/${BASH_REMATCH[1]}"
elif [[ "$REPO_URL" =~ ^https://github\.com/(.+)\.git$ ]]; then
  REPO_HTTP="https://github.com/${BASH_REMATCH[1]}"
else
  REPO_HTTP="$REPO_URL"
fi

REPO_PATH="${REPO_HTTP#https://github.com/}"
BROWSE_LINK="$REPO_HTTP/tree/$BRANCH/apk-artifacts/$BUILD_TYPE"
DOWNLOAD_LINK="https://raw.githubusercontent.com/$REPO_PATH/$BRANCH/apk-artifacts/$BUILD_TYPE/latest-$BUILD_TYPE.apk"

echo
echo "APK publicada correctamente."
echo "Archivo local: $APK_PATH"
echo "Repositorio: $REPO_HTTP"
echo "Carpeta publicada: $BROWSE_LINK"
echo "Descarga directa: $DOWNLOAD_LINK"

popd >/dev/null
popd >/dev/null
