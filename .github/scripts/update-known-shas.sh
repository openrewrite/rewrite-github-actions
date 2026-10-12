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
TAG_RE='^v?[0-9]'
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

  # Minor/patch tags are immutable; only major tags (v1, 2, ...) and branch refs can drift
  if [[ "$tag" =~ ^v?[0-9]+\.[0-9] ]]; then
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

# Well-known actions that should always be included even if not referenced
# in local workflow files. Add new actions here to seed them.
SEED_ACTIONS=(
  "actions/add-to-project"
  "actions/ai-inference"
  "actions/attest"
  "actions/attest-build-provenance"
  "actions/attest-sbom"
  "actions/cache"
  "actions/cache/restore"
  "actions/cache/save"
  "actions/checkout"
  "actions/configure-pages"
  "actions/create-github-app-token"
  "actions/create-release"
  "actions/delete-package-versions"
  "actions/dependency-review-action"
  "actions/deploy-pages"
  "actions/download-artifact"
  "actions/first-interaction"
  "actions/github-script"
  "actions/jekyll-build-pages"
  "actions/labeler"
  "actions/setup-dotnet"
  "actions/setup-go"
  "actions/setup-java"
  "actions/setup-node"
  "actions/setup-python"
  "actions/stale"
  "actions/upload-artifact"
  "actions/upload-pages-artifact"
  "actions/upload-release-asset"
  "1password/load-secrets-action"
  "8398a7/action-slack"
  "abatilo/actions-poetry"
  "actions-rs/cargo"
  "actions-rs/toolchain"
  "actions-rust-lang/setup-rust-toolchain"
  "ad-m/github-push-action"
  "amannn/action-semantic-pull-request"
  "aminya/setup-cpp"
  "amondnet/vercel-action"
  "anchore/sbom-action"
  "anchore/scan-action"
  "android-actions/setup-android"
  "anothrNick/github-tag-action"
  "anthropics/claude-code-action"
  "apple-actions/import-codesign-certs"
  "apple-actions/upload-testflight-build"
  "appleboy/ssh-action"
  "appleboy/telegram-action"
  "aquasecurity/trivy-action"
  "arduino/setup-task"
  "astral-sh/ruff-action"
  "astral-sh/setup-uv"
  "aws-actions/amazon-ecr-login"
  "aws-actions/amazon-ecs-deploy-task-definition"
  "aws-actions/amazon-ecs-render-task-definition"
  "aws-actions/aws-cloudformation-github-deploy"
  "aws-actions/configure-aws-credentials"
  "aws-actions/setup-sam"
  "azure/aks-set-context"
  "azure/arm-deploy"
  "azure/cli"
  "azure/docker-login"
  "azure/functions-action"
  "azure/k8s-deploy"
  "azure/k8s-set-context"
  "azure/login"
  "azure/setup-helm"
  "azure/setup-kubectl"
  "azure/static-web-apps-deploy"
  "azure/webapps-deploy"
  "bahmutov/npm-install"
  "bazel-contrib/setup-bazel"
  "benc-uk/workflow-dispatch"
  "biomejs/setup-biome"
  "bridgecrewio/checkov-action"
  "burnett01/rsync-deployments"
  "cachix/cachix-action"
  "cachix/install-nix-action"
  "cargo-bins/cargo-binstall"
  "changesets/action"
  "chromaui/action"
  "cloudflare/wrangler-action"
  "codecov/codecov-action"
  "codespell-project/actions-codespell"
  "conda-incubator/setup-miniconda"
  "coursier/cache-action"
  "coursier/setup-action"
  "coverallsapp/github-action"
  "crate-ci/typos"
  "crazy-max/ghaction-github-pages"
  "crazy-max/ghaction-import-gpg"
  "crystal-lang/install-crystal"
  "cycjimmy/semantic-release-action"
  "cypress-io/github-action"
  "dart-lang/setup-dart"
  "DavidAnson/markdownlint-cli2-action"
  "dawidd6/action-send-mail"
  "DeLaGuardo/setup-clojure"
  "denoland/setup-deno"
  "dependabot/fetch-metadata"
  "dessant/lock-threads"
  "DeterminateSystems/magic-nix-cache-action"
  "DeterminateSystems/nix-installer-action"
  "dflook/terraform-apply"
  "dflook/terraform-plan"
  "digitalocean/action-doctl"
  "docker/bake-action"
  "docker/build-push-action"
  "docker/login-action"
  "docker/metadata-action"
  "docker/scout-action"
  "docker/setup-buildx-action"
  "docker/setup-docker-action"
  "docker/setup-qemu-action"
  "dorny/paths-filter"
  "dorny/test-reporter"
  "dtolnay/rust-toolchain"
  "easimon/maximize-build-space"
  "EmbarkStudios/cargo-deny-action"
  "EndBug/add-and-commit"
  "EnricoMi/publish-unit-test-result-action"
  "erlef/setup-beam"
  "fastify/github-action-merge-dependabot"
  "FirebaseExtended/action-hosting-deploy"
  "fluxcd/flux2/action"
  "game-ci/unity-builder"
  "getsentry/action-release"
  "github/codeql-action/analyze"
  "github/codeql-action/autobuild"
  "github/codeql-action/init"
  "github/codeql-action/resolve-environment"
  "github/codeql-action/upload-sarif"
  "github/issue-labeler"
  "github/super-linter"
  "gitleaks/gitleaks-action"
  "gittools/actions/gitversion/execute"
  "gittools/actions/gitversion/setup"
  "golang/govulncheck-action"
  "golangci/golangci-lint-action"
  "google-github-actions/auth"
  "google-github-actions/deploy-appengine"
  "google-github-actions/deploy-cloudrun"
  "google-github-actions/get-gke-credentials"
  "google-github-actions/get-secretmanager-secrets"
  "google-github-actions/release-please-action"
  "google-github-actions/run-gemini-cli"
  "google-github-actions/setup-gcloud"
  "google-github-actions/upload-cloud-storage"
  "googleapis/release-please-action"
  "goreleaser/goreleaser-action"
  "graalvm/setup-graalvm"
  "gradle/actions/dependency-submission"
  "gradle/actions/setup-gradle"
  "gradle/actions/wrapper-validation"
  "gradle/gradle-build-action"
  "hadolint/hadolint-action"
  "hashicorp/setup-packer"
  "hashicorp/setup-terraform"
  "hashicorp/vault-action"
  "haskell-actions/setup"
  "helm/chart-releaser-action"
  "helm/chart-testing-action"
  "helm/kind-action"
  "hendrikmuhs/ccache-action"
  "hmarr/auto-approve-action"
  "houseabsolute/actions-rust-cross"
  "ilammy/msvc-dev-cmd"
  "Ilshidur/action-discord"
  "infracost/actions/setup"
  "irgaly/xcode-cache"
  "jakebailey/pyright-action"
  "JamesIves/github-pages-deploy-action"
  "JetBrains/qodana-action"
  "Jimver/cuda-toolkit"
  "jlumbroso/free-disk-space"
  "JS-DevTools/npm-publish"
  "julia-actions/cache"
  "julia-actions/julia-buildpkg"
  "julia-actions/julia-runtest"
  "julia-actions/setup-julia"
  "jurplel/install-qt-action"
  "jwlawson/actions-setup-cmake"
  "ko-build/setup-ko"
  "KyleMayes/install-llvm-action"
  "leafo/gh-actions-lua"
  "lewagon/wait-on-check-action"
  "ludeeus/action-shellcheck"
  "lukka/get-cmake"
  "lukka/run-vcpkg"
  "lycheeverse/lychee-action"
  "madrapps/jacoco-report"
  "maierj/fastlane-action"
  "mamba-org/setup-micromamba"
  "marocchino/sticky-pull-request-comment"
  "mathieudutour/github-tag-action"
  "maxim-lobanov/setup-xcode"
  "medyagh/setup-minikube"
  "microsoft/setup-msbuild"
  "mikefarah/yq"
  "mikepenz/action-junit-report"
  "mikepenz/release-changelog-builder-action"
  "mlugg/setup-zig"
  "mozilla-actions/sccache-action"
  "mshick/add-pr-comment"
  "msys2/setup-msys2"
  "mxschmitt/action-tmate"
  "ncipollo/release-action"
  "nick-fields/retry"
  "nrwl/nx-set-shas"
  "NuGet/login"
  "NuGet/setup-nuget"
  "ocaml/setup-ocaml"
  "openai/codex-action"
  "opentofu/setup-opentofu"
  "orhun/git-cliff-action"
  "ossf/scorecard-action"
  "oven-sh/setup-bun"
  "oxsecurity/megalinter"
  "pascalgn/automerge-action"
  "pdm-project/setup-pdm"
  "peaceiris/actions-gh-pages"
  "peaceiris/actions-hugo"
  "peaceiris/actions-mdbook"
  "peter-evans/create-or-update-comment"
  "peter-evans/create-pull-request"
  "peter-evans/dockerhub-description"
  "peter-evans/enable-pull-request-automerge"
  "peter-evans/find-comment"
  "peter-evans/repository-dispatch"
  "peter-evans/slash-command-dispatch"
  "php-actions/composer"
  "pnpm/action-setup"
  "pre-commit/action"
  "preactjs/compressed-size-action"
  "prefix-dev/setup-pixi"
  "pulumi/actions"
  "PyO3/maturin-action"
  "pypa/cibuildwheel"
  "pypa/gh-action-pypi-publish"
  "r-lib/actions/check-r-package"
  "r-lib/actions/setup-pandoc"
  "r-lib/actions/setup-r"
  "r-lib/actions/setup-r-dependencies"
  "r0adkll/upload-google-play"
  "ramsey/composer-install"
  "raven-actions/actionlint"
  "re-actors/alls-green"
  "reactivecircus/android-emulator-runner"
  "redhat-actions/buildah-build"
  "redhat-actions/podman-login"
  "redhat-actions/push-to-registry"
  "release-drafter/release-drafter"
  "release-plz/action"
  "reviewdog/action-eslint"
  "reviewdog/action-golangci-lint"
  "reviewdog/action-setup"
  "rlespinasse/github-slug-action"
  "robinraju/release-downloader"
  "rtCamp/action-slack-notify"
  "ruby/setup-ruby"
  "rubygems/release-gem"
  "rustsec/audit-check"
  "s4u/maven-settings-action"
  "sarisia/actions-status-discord"
  "sbt/setup-sbt"
  "securego/gosec"
  "shimataro/ssh-key-action"
  "shivammathur/setup-php"
  "shogo82148/actions-setup-perl"
  "sigstore/cosign-installer"
  "slackapi/slack-github-action"
  "snok/install-poetry"
  "snyk/actions/docker"
  "snyk/actions/golang"
  "snyk/actions/maven"
  "snyk/actions/node"
  "snyk/actions/python"
  "softprops/action-gh-release"
  "SonarSource/sonarcloud-github-action"
  "SonarSource/sonarqube-quality-gate-action"
  "SonarSource/sonarqube-scan-action"
  "stCarolas/setup-maven"
  "stefanzweifel/git-auto-commit-action"
  "step-security/changed-files"
  "step-security/harden-runner"
  "streetsidesoftware/cspell-action"
  "styfle/cancel-workflow-action"
  "subosito/flutter-action"
  "super-linter/super-linter"
  "superfly/flyctl-actions/setup-flyctl"
  "svenstaro/upload-release-action"
  "Swatinem/rust-cache"
  "swift-actions/setup-swift"
  "taiki-e/create-gh-release-action"
  "taiki-e/install-action"
  "taiki-e/upload-rust-binary-action"
  "tailscale/github-action"
  "terraform-linters/setup-tflint"
  "thollander/actions-comment-pull-request"
  "tj-actions/changed-files"
  "treosh/lighthouse-ci-action"
  "trufflesecurity/trufflehog"
  "wagoid/commitlint-github-action"
  "webfactory/ssh-agent"
  "withastro/action"
  "zizmorcore/zizmor-action"
)

# Combine actions from the file with the seed list
KNOWN_ACTIONS=$(
  {
    grep -v '^#' "$PROPS_FILE" | grep -v '^$' | sed 's/@.*//'
    printf '%s\n' "${SEED_ACTIONS[@]}"
  } | sort -u
)

while IFS= read -r action_path; do
  [[ -z "$action_path" ]] && continue
  owner_repo=$(owner_repo_of "$action_path")

  # Fetch all version tags with their commit SHAs: v1, v1.2, v1.2.3, 1.2.3
  # (not pre-release or non-numeric suffixes). The tags API already reports
  # the commit an annotated tag points at, so no per-tag lookup is needed.
  # `|| true` keeps the script alive when the action has no matching tags
  # (grep exits 1 on no matches, which would otherwise trip `pipefail`).
  TAGS=$(gh api "repos/${owner_repo}/tags" --paginate --jq '.[] | "\(.name) \(.commit.sha)"' 2>/dev/null \
    | grep -E '^v?[0-9]+(\.[0-9]+){0,2} ' | sort -V) || true

  while read -r tag sha; do
    [[ -z "$tag" ]] && continue
    ref="${action_path}@${tag}"

    # Skip if already present
    if grep -qF "${ref}=" "$PROPS_FILE" 2>/dev/null; then
      continue
    fi

    if [[ "$sha" =~ $SHA_RE ]]; then
      echo "Resolved: ${ref} → ${sha}"
      echo "${ref}=${sha}" >> "$PROPS_FILE"
      ADDED+=("${ref}=${sha}")
    fi
  done <<< "$TAGS"
done <<< "$KNOWN_ACTIONS"

# Branch refs that are commonly used in place of a version tag. The tags API
# doesn't list them, so they're resolved here and kept fresh by the drift check.
SEED_REFS=(
  "aquasecurity/trivy-action@master"
  "dtolnay/rust-toolchain@beta"
  "dtolnay/rust-toolchain@master"
  "dtolnay/rust-toolchain@nightly"
  "dtolnay/rust-toolchain@stable"
  "Ilshidur/action-discord@master"
  "ludeeus/action-shellcheck@master"
  "mikefarah/yq@master"
  "pypa/gh-action-pypi-publish@release/v1"
  "ramsey/composer-install@v2"
  "ramsey/composer-install@v3"
)

for ref in "${SEED_REFS[@]}"; do
  if grep -qF "${ref}=" "$PROPS_FILE" 2>/dev/null; then
    continue
  fi

  action_path="${ref%%@*}"
  sha=$(resolve_sha "$(owner_repo_of "$action_path")" "${ref#*@}")
  if [[ "$sha" =~ $SHA_RE ]]; then
    echo "Resolved: ${ref} → ${sha}"
    echo "${ref}=${sha}" >> "$PROPS_FILE"
    ADDED+=("${ref}=${sha}")
  fi
done

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
