#!/usr/bin/env bash
set -euo pipefail

ROOT="docs"
API_DOC="$ROOT/api-interface-spec-voice-phone-finder-mvp.md"
SPEC_DOC="$ROOT/technical-spec-voice-phone-finder-mvp.md"
DATA_DOC="$ROOT/data-storage-design-voice-phone-finder-mvp.md"
PRIV_DOC="$ROOT/permissions-security-privacy-voice-phone-finder-mvp.md"
PRD_DOC="$ROOT/prd-voice-phone-finder-mvp.md"
RELEASE_WF=".github/workflows/release-check.yml"
BRANCH_PROTECT_SCRIPT="scripts/ci/apply_branch_protection.sh"
REQ_FILES=(
  "$SPEC_DOC"
  "$ROOT/technical-design-voice-phone-finder-mvp.md"
  "$API_DOC"
  "$DATA_DOC"
  "$PRIV_DOC"
  "$PRD_DOC"
  "$RELEASE_WF"
  "$BRANCH_PROTECT_SCRIPT"
)

for f in "${REQ_FILES[@]}"; do
  [[ -f "$f" ]] || { echo "Missing required doc: $f"; exit 1; }
done

must_have=(
  "VoiceDetectionEngine"
  "updateKeywords"
  "AlertPlayer"
  "playSequence"
  "triggered_keyword"
  "confirm_phrase"
  "stop_phrases"
  "FOREGROUND_SERVICE_MICROPHONE"
  "READY_TO_RESUME"
)

for token in "${must_have[@]}"; do
  if ! rg -q "$token" "$ROOT"/*.md; then
    echo "Missing token across docs: $token"
    exit 1
  fi
done

# Cross-doc exact checks for critical contracts.
rg -q "interface VoiceDetectionEngine" "$API_DOC"
rg -q "interface VoiceDetectionEngine" "$SPEC_DOC"
rg -q "fun updateKeywords\(keywords: List<String>\)" "$API_DOC"
rg -q "fun updateKeywords\(keywords: List<String>\)" "$SPEC_DOC"
rg -q "suspend fun playSequence\(policy: PlaybackPolicy\)" "$API_DOC"
rg -q "suspend fun playSequence\(policy: PlaybackPolicy\)" "$SPEC_DOC"

# Ensure API UserSettings stays aligned to spec: no extra keyword list field.
api_usersettings_block="$(awk '
  /data class UserSettings\(/ {in_block=1}
  in_block {print}
  in_block && /^\)/ {in_block=0}
' "$API_DOC")"
if echo "$api_usersettings_block" | rg -q "val keywords:"; then
  echo "API UserSettings must not contain keywords field (spec mismatch)."
  exit 1
fi

# Schema keys must align between technical spec and data storage design.
rg -q "triggered_keyword TEXT NOT NULL" "$SPEC_DOC"
rg -q "triggered_keyword TEXT NOT NULL" "$DATA_DOC"
rg -q "confirm_phrase" "$SPEC_DOC"
rg -q "confirm_phrase" "$DATA_DOC"
rg -q "stop_phrases" "$SPEC_DOC"
rg -q "stop_phrases" "$DATA_DOC"

# Naming mapping contract must be explicitly documented.
rg -q "snake_case <-> camelCase 매핑 규칙" "$API_DOC"
rg -q "triggeredKeywordProtected" "$API_DOC"

# Permission policy consistency: notifications should be optional in PRD + privacy doc.
rg -q "선택 권한\(Android 13\+\): 알림 권한" "$PRD_DOC"
rg -q "선택 권한" "$PRIV_DOC"

# Release workflow must include release-specific gates beyond shared full_gate.
rg -q "release_versioning_guard" "$RELEASE_WF"
rg -q "release_signing_guard" "$RELEASE_WF"

# Branch protection script must include all required check contexts.
rg -q "\"preflight\"" "$BRANCH_PROTECT_SCRIPT"
rg -q "\"docs_consistency\"" "$BRANCH_PROTECT_SCRIPT"
rg -q "\"lint_and_static\"" "$BRANCH_PROTECT_SCRIPT"
rg -q "\"architecture_rules\"" "$BRANCH_PROTECT_SCRIPT"
rg -q "\"build_and_unit\"" "$BRANCH_PROTECT_SCRIPT"
rg -q "\"db_migration\"" "$BRANCH_PROTECT_SCRIPT"
rg -q "\"security_privacy\"" "$BRANCH_PROTECT_SCRIPT"

# Forbidden stale tokens.
! rg -q "triggered_keyword_masked|confirm_phrase_cipher|stop_phrases_cipher|DataStore/Room" "$ROOT"/*.md \
  || { echo "Found stale/contradictory token in docs"; exit 1; }

echo "Docs consistency check passed"
