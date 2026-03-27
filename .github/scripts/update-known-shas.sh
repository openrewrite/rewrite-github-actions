#!/usr/bin/env bash
#
# Resolves GitHub Action tag references to commit SHAs and updates the
# known-action-shas.properties file. Adds new mappings and refreshes
# any existing entries whose floating tags have moved.
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

# Helper: extract owner/repo from an action path (strips subpath)
owner_repo_of() {
  local action_path="$1"
  if [[ "$action_path" =~ ^([^/]+/[^/]+)/.+ ]]; then
    echo "${BASH_REMATCH[1]}"
  else
    echo "$action_path"
  fi
}

# Helper: resolve a tag to a commit SHA via the GitHub API
resolve_sha() {
  local owner_repo="$1" tag="$2"
  gh api "repos/${owner_repo}/commits/${tag}" --jq '.sha' 2>/dev/null || true
}

ADDED=()
UPDATED=()

# ------------------------------------------------------------------
# 1. Refresh existing entries whose SHAs may have drifted
# ------------------------------------------------------------------
echo "Checking existing entries for drifted SHAs..."

TMP_PROPS=$(mktemp)
while IFS= read -r line; do
  # Pass through comments and blank lines
  if [[ "$line" =~ ^# ]] || [[ -z "$line" ]]; then
    echo "$line" >> "$TMP_PROPS"
    continue
  fi

  ref="${line%%=*}"
  old_sha="${line#*=}"
  action_path="${ref%%@*}"
  tag="${ref#*@}"

  # Only check drift on major version tags (v1, v2, ...) since minor/patch tags are immutable
  if ! [[ "$tag" =~ ^v[0-9]+$ ]]; then
    echo "$line" >> "$TMP_PROPS"
    continue
  fi

  owner_repo=$(owner_repo_of "$action_path")

  new_sha=$(resolve_sha "$owner_repo" "$tag")

  if [[ -n "$new_sha" ]] && [[ "$new_sha" =~ $SHA_RE ]] && [[ "$new_sha" != "$old_sha" ]]; then
    echo "Updated: ${ref} ${old_sha} → ${new_sha}"
    echo "${ref}=${new_sha}" >> "$TMP_PROPS"
    UPDATED+=("${ref}")
  else
    echo "$line" >> "$TMP_PROPS"
  fi
done < "$PROPS_FILE"

mv "$TMP_PROPS" "$PROPS_FILE"

# ------------------------------------------------------------------
# 2. Expand: resolve all version tags for actions already in the file
# ------------------------------------------------------------------
echo ""
echo "Expanding all version tags for known actions..."

# Collect unique action paths (without @tag) from existing entries
KNOWN_ACTIONS=$(grep -v '^#' "$PROPS_FILE" | grep -v '^$' | sed 's/@.*//' | sort -u)

while IFS= read -r action_path; do
  [[ -z "$action_path" ]] && continue
  owner_repo=$(owner_repo_of "$action_path")

  # Fetch all version tags: v1, v1.2, v1.2.3 (not pre-release or non-numeric suffixes)
  TAGS=$(gh api "repos/${owner_repo}/tags" --paginate --jq '.[].name' 2>/dev/null \
    | grep -E '^v[0-9]+(\.[0-9]+){0,2}$' | sort -V)

  while IFS= read -r tag; do
    [[ -z "$tag" ]] && continue
    ref="${action_path}@${tag}"

    # Skip if already present
    if grep -qF "${ref}=" "$PROPS_FILE" 2>/dev/null; then
      continue
    fi

    sha=$(resolve_sha "$owner_repo" "$tag")
    if [[ -n "$sha" ]] && [[ "$sha" =~ $SHA_RE ]]; then
      echo "Resolved: ${ref} → ${sha}"
      echo "${ref}=${sha}" >> "$PROPS_FILE"
      ADDED+=("${ref}=${sha}")
    fi
  done <<< "$TAGS"
done <<< "$KNOWN_ACTIONS"

# ------------------------------------------------------------------
# 3. Add new entries from workflow files
# ------------------------------------------------------------------
echo ""
echo "Scanning ${WORKFLOW_DIR} for new action references..."

USES_REFS=$(
  grep -rh 'uses:' "$WORKFLOW_DIR" \
    | sed 's/.*uses:[[:space:]]*//' \
    | tr -d '"'"'" \
    | sed 's/#.*//' \
    | tr -d '[:space:]' \
    | sort -u
)

while IFS= read -r ref; do
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

  owner_repo=$(owner_repo_of "$action_path")
  sha=$(resolve_sha "$owner_repo" "$tag")

  if [[ -z "$sha" ]] || ! [[ "$sha" =~ $SHA_RE ]]; then
    echo "Warning: Could not resolve ${ref} — skipping"
    continue
  fi

  echo "Resolved: ${ref} → ${sha}"
  echo "${ref}=${sha}" >> "$PROPS_FILE"
  ADDED+=("${ref}=${sha}")
done <<< "$USES_REFS"

# ------------------------------------------------------------------
# 4. Sort the properties file (preserve header comments)
# ------------------------------------------------------------------
{
  grep '^#' "$PROPS_FILE" || true
  echo ""
  grep -v '^#' "$PROPS_FILE" | grep -v '^$' | sort
} > /tmp/sorted.properties
mv /tmp/sorted.properties "$PROPS_FILE"

# ------------------------------------------------------------------
# 5. Summary
# ------------------------------------------------------------------
echo ""
if [[ ${#UPDATED[@]} -gt 0 ]]; then
  echo "Updated ${#UPDATED[@]} drifted entries:"
  for entry in "${UPDATED[@]}"; do
    echo "  ${entry}"
  done
else
  echo "No drifted entries found."
fi

if [[ ${#ADDED[@]} -gt 0 ]]; then
  echo "Added ${#ADDED[@]} new entries:"
  for entry in "${ADDED[@]}"; do
    echo "  ${entry}"
  done
else
  echo "No new entries to add."
fi
