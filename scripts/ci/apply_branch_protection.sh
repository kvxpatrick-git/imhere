#!/usr/bin/env bash
set -euo pipefail

if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI is required"
  exit 1
fi

repo="${1:-}"
branch="${2:-main}"

if [[ -z "$repo" ]]; then
  echo "Usage: $0 <owner/repo> [branch]"
  exit 1
fi

# Requires GH_TOKEN with repo admin permission.
payload="$(cat <<'JSON'
{
  "required_status_checks": {
    "strict": true,
    "contexts": [
      "preflight",
      "docs_consistency",
      "lint_and_static",
      "architecture_rules",
      "build_and_unit",
      "db_migration",
      "security_privacy"
    ]
  },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "dismiss_stale_reviews": true,
    "required_approving_review_count": 1
  },
  "restrictions": null,
  "required_linear_history": true,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "block_creations": false,
  "required_conversation_resolution": true
}
JSON
)"

gh api \
  --method PUT \
  -H "Accept: application/vnd.github+json" \
  "/repos/${repo}/branches/${branch}/protection" \
  --input - <<<"$payload"

echo "Branch protection applied for ${repo}:${branch}"
