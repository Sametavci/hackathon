#!/usr/bin/env bash
# One-command AWS deploy: build -> store secrets in SSM -> cdk bootstrap/deploy -> wait for the app.
# Needs: AWS CLI v2 with credentials (aws configure), JDK 21, Node 20+.
# Optional env: AWS_REGION (default eu-central-1), ANTHROPIC_API_KEY, LUNCH2_ADMIN_KEY.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export AWS_REGION="${AWS_REGION:-eu-central-1}"

command -v aws >/dev/null || { echo "AWS CLI missing: brew install awscli && aws configure"; exit 1; }
CDK_DEFAULT_ACCOUNT="$(aws sts get-caller-identity --query Account --output text)" \
  || { echo "No AWS credentials: run aws configure"; exit 1; }
export CDK_DEFAULT_ACCOUNT
echo "==> Account $CDK_DEFAULT_ACCOUNT, region $AWS_REGION"

# The deploy ships whatever is in this folder, so refuse half-finished local work unless asked.
if [ -n "$(git -C "$ROOT" status --porcelain)" ] && [ -z "${ALLOW_DIRTY:-}" ]; then
  echo "Uncommitted changes in $ROOT would be deployed:"; git -C "$ROOT" status --short | head -20
  echo "Commit/stash them (deploy from an up-to-date main), or rerun with ALLOW_DIRTY=1."; exit 1
fi

"$ROOT/scripts/build-all.sh"

echo "==> Storing secrets in SSM Parameter Store (/lunch2/*)"
if ! aws ssm get-parameter --name /lunch2/admin-key >/dev/null 2>&1 || [ -n "${LUNCH2_ADMIN_KEY:-}" ]; then
  ADMIN_KEY="${LUNCH2_ADMIN_KEY:-$(openssl rand -hex 16)}"
  aws ssm put-parameter --name /lunch2/admin-key --type SecureString --value "$ADMIN_KEY" --overwrite >/dev/null
fi
if [ -n "${ANTHROPIC_API_KEY:-}" ]; then
  aws ssm put-parameter --name /lunch2/anthropic-api-key --type SecureString --value "$ANTHROPIC_API_KEY" --overwrite >/dev/null
fi

echo "==> Deploying with CDK"
cd "$ROOT/infra"
npm ci --silent
npx cdk bootstrap "aws://$CDK_DEFAULT_ACCOUNT/$AWS_REGION" >/dev/null
npm run -s deploy

URL="$(node -p "require('./cdk-outputs.json').Lunch2.Url")"
echo "==> Waiting for $URL (first boot installs Java, ~2-3 min)"
for _ in $(seq 1 60); do
  if curl -fs "$URL/api/ping" >/dev/null 2>&1; then
    echo "==> Live: $URL"
    echo "    Admin key: aws ssm get-parameter --name /lunch2/admin-key --with-decryption --query Parameter.Value --output text"
    exit 0
  fi
  sleep 5
done
echo "App did not answer within 5 min. Debug: $(node -p "require('./cdk-outputs.json').Lunch2.ShellCommand") then: sudo journalctl -u lunch2 -u cloud-final"
exit 1
