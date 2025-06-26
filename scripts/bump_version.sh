#!/usr/bin/env bash
set -e

VERSION_FILE="src/main/resources/version.txt"
MODE="${1:-dev}"

if [ ! -f "$VERSION_FILE" ]; then
  echo "0.1.0-dev" > "$VERSION_FILE"
  echo "No version found. Set to 0.1.0-dev."
  exit 0
fi

CURRENT_VERSION=$(cat "$VERSION_FILE")
BASE_VERSION=${CURRENT_VERSION%%-*}
SUFFIX=${CURRENT_VERSION#*-}
if [[ "$CURRENT_VERSION" == *-* ]]; then
  SUFFIX="-${SUFFIX}"
else
  SUFFIX=""
fi
IFS='.' read -r MAJOR MINOR PATCH <<< "$BASE_VERSION"

if [ "$MODE" = "release" ]; then
  NEW_VERSION="$MAJOR.$MINOR.$PATCH"
  echo "$NEW_VERSION" > "$VERSION_FILE"
  echo "Release version: $CURRENT_VERSION -> $NEW_VERSION"
else
  PATCH=$((PATCH + 1))
  NEW_VERSION="$MAJOR.$MINOR.$PATCH"
  if [[ "$SUFFIX" != "-dev" ]]; then
    NEW_VERSION="$NEW_VERSION-dev"
  fi
  echo "$NEW_VERSION" > "$VERSION_FILE"
  echo "Dev version bumped: $CURRENT_VERSION -> $NEW_VERSION"
fi 