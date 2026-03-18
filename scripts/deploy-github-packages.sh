#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${GITHUB_TOKEN:-}" ]]; then
  echo "GITHUB_TOKEN is required"
  exit 1
fi

OWNER="${GITHUB_OWNER:-momotech}"
REPO="${GITHUB_REPO:-linkwork-server}"
SETTINGS_FILE="${SETTINGS_FILE:-settings-github.xml.example}"

mvn -s "${SETTINGS_FILE}" \
  -DskipTests \
  -Dgithub.owner="${OWNER}" \
  -Dgithub.repo="${REPO}" \
  deploy
