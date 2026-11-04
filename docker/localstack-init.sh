#!/usr/bin/env bash
# Runs once LocalStack is ready (mounted in /etc/localstack/init/ready.d): the bucket the backend writes to (HERACLES_STORAGE_BUCKET).
# In AWS the bucket comes from OpenTofu (infra/modules/storage); this is only the local twin of it.
set -euo pipefail
awslocal s3 mb "s3://${HERACLES_STORAGE_BUCKET:-heracles-files}" || true
