#!/usr/bin/env bash
set -euo pipefail

# Lightweight architecture guard that works even before full Gradle tasks exist.
if ! command -v rg >/dev/null 2>&1; then
  echo "rg is required"
  exit 1
fi

# If project source modules do not exist yet, skip safely.
if [[ ! -d core-domain && ! -d core-data && ! -d feature-listening && ! -d platform-voice ]]; then
  echo "No source modules found. Skipping architecture import checks."
  exit 0
fi

violations=0

if [[ -d core-domain ]]; then
  if rg -n "^import (android\\.|androidx\\.|.*room|.*datastore|.*compose)" core-domain --glob "**/*.kt"; then
    echo "Violation: core-domain contains forbidden framework/storage/ui imports"
    violations=1
  fi
  if rg -n "^import .*core\\.data|^import .*platform\\.|^import .*feature\\.|^import .*app\\." core-domain --glob "**/*.kt"; then
    echo "Violation: core-domain depends on outer layers"
    violations=1
  fi
fi

if [[ -d feature-listening ]]; then
  if rg -n "^import .*platform\\." feature-listening --glob "**/*.kt"; then
    echo "Violation: feature-* directly depends on platform-*"
    violations=1
  fi
fi

if [[ "$violations" -ne 0 ]]; then
  exit 1
fi

echo "Architecture import checks passed"
