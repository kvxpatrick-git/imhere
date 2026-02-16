#!/usr/bin/env bash
set -euo pipefail

# Works in both code and docs-first repositories.
if [[ ! -f ./gradlew ]]; then
  echo "No Gradle project found. Skipping Room schema gate."
  exit 0
fi

# Run export if task exists.
if ! tasks_output="$(./gradlew tasks --all 2>&1)"; then
  echo "Failed to enumerate Gradle tasks for Room schema gate"
  echo "$tasks_output"
  exit 1
fi

if echo "$tasks_output" | rg -q "exportRoomSchema"; then
  ./gradlew :core-data:exportRoomSchema
else
  echo "exportRoomSchema task not found"
fi

# Heuristic check: if entity files changed in PR, schemas must also change.
base_ref="${GITHUB_BASE_REF:-}"
if [[ -n "$base_ref" ]]; then
  git fetch --depth=1 origin "$base_ref"
  changed_files="$(git diff --name-only "origin/$base_ref"...HEAD)"
  if echo "$changed_files" | rg -q "Entity\\.kt|@Entity|core-data/.*/entity"; then
    if ! echo "$changed_files" | rg -q "schemas/.*\\.json"; then
      echo "Room entity changed but schema json not updated"
      exit 1
    fi
  fi
fi

echo "Room schema checks passed"
