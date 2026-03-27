#!/usr/bin/env bash
#
# Resolves GitHub Action tag references to commit SHAs and appends any
# new mappings to the known-action-shas.properties file.
#
# Prerequisites: gh CLI authenticated (GH_TOKEN or `gh auth login`)
#
# Usage:
#   ./update-known-shas.sh                          # scan .github/workflows/
#   ./update-known-shas.sh path/to/workflows/       # scan a custom directory
#
set -euo pipefail

PROPS_FILE="src/main/resources/META-INF/rewrite/known-action-shas.properties"
WORKFLOW_DIR="${1:-.github/workflows/}"
TAG_RE='^v[0-9]'
SHA_RE='^[a-f0-9]{40}$'

if [[ ! -f "$PROPS_FILE" ]]; then
  echo "Error: $PROPS_FILE not found. Run from the repository root." >&2
  exit 1
fi

# ------------------------------------------------------------------
# 1. Extract every "uses:" value from workflow files
# ------------------------------------------------------------------
mapfile -t USES_REFS < <(
  grep -rh 'uses:' "$WORKFLOW_DIR" \
    | sed 's/.*uses:[[:space:]]*//' \
    | tr -d '"'"'" \
    | sed 's/#.*//' \
    | tr -d '[:space:]' \
    | sort -u
)

ADDED_KEYS=()

for ref in "${USES_REFS[@]}"; do
  # Skip blanks, local actions, docker refs
  [[ -z "$ref" ]] && continue
  [[ "$ref" == ./* ]] && continue
  [[ "$ref" == docker://* ]] && continue

  # Must match owner/repo@something or owner/repo/subpath@something
  if ! [[ "$ref" =~ ^([^/@]+/[^/@]+(/[^@]+)?)@(.+)$ ]]; then
    continue
  fi

  action_path="${BASH_REMATCH[1]}"
  tag="${BASH_REMATCH[3]}"

  # Skip already-pinned SHAs
  if [[ "$tag" =~ $SHA_RE ]]; then
    continue
  fi

  # Only include tags (e.g. v1, v2.3), skip branches
  if ! [[ "$tag" =~ $TAG_RE ]]; then
    echo "Skipping branch ref: ${ref}"
    continue
  fi

  # Skip if already present in the properties file
  if grep -qF "${ref}=" "$PROPS_FILE" 2>/dev/null; then
    continue
  fi

  # Extract owner/repo (strip subpath)
  owner_repo="$action_path"
  if [[ "$action_path" =~ ^([^/]+/[^/]+)/.+ ]]; then
    owner_repo="${BASH_REMATCH[1]}"
  fi

  # Resolve tag → commit SHA via GitHub API
  sha=$(gh api "repos/${owner_repo}/commits/${tag}" \
          --jq '.sha' 2>/dev/null || true)

  if [[ -z "$sha" ]] || ! [[ "$sha" =~ $SHA_RE ]]; then
    echo "Warning: Could not resolve ${ref} — skipping"
    continue
  fi

  echo "Resolved: ${ref} → ${sha}"
  echo "${ref}=${sha}" >> "$PROPS_FILE"
  ADDED_KEYS+=("${ref}=${sha}")
done

if [[ ${#ADDED_KEYS[@]} -eq 0 ]]; then
  echo "No new entries to add."
  exit 0
fi

# ------------------------------------------------------------------
# 2. Sort the properties file (preserve header comments)
# ------------------------------------------------------------------
{
  grep '^#' "$PROPS_FILE" || true
  echo ""
  grep -v '^#' "$PROPS_FILE" | grep -v '^$' | sort
} > /tmp/sorted.properties
mv /tmp/sorted.properties "$PROPS_FILE"

echo ""
echo "Added ${#ADDED_KEYS[@]} new entries:"
for entry in "${ADDED_KEYS[@]}"; do
  echo "  ${entry}"
done
