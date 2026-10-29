#!/usr/bin/env bash
# Removes the AWS stack (instance, IP, VPC). SSM secrets and the CDK bootstrap stack are kept.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export AWS_REGION="${AWS_REGION:-eu-central-1}"
CDK_DEFAULT_ACCOUNT="$(aws sts get-caller-identity --query Account --output text)"; export CDK_DEFAULT_ACCOUNT
cd "$ROOT/infra" && npm run -s destroy
