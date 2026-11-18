#!/usr/bin/env sh
# Static analysis with Semgrep in Docker (pinned image, metrics off): the
# project rules in .semgrep.yml plus registry rulesets for Java, TypeScript,
# React, secrets, Dockerfiles, Docker Compose and nginx. Run from anywhere:
#   scripts/semgrep.sh                         # text report (exit 0 even with findings)
#   scripts/semgrep.sh --error                 # exit 1 when there are findings
#   scripts/semgrep.sh --json -o semgrep.json  # machine-readable report (git-ignored)
# Needs Docker and network access to download the image and the rulesets.
# Behind a TLS-inspecting proxy, export MATRIZ_EXTRA_CA_CERT with the path of
# the proxy's root certificate (PEM, outside the repository), as for the
# frontend image build; this script reads the environment, not .env.
set -eu

cd "$(dirname "$0")/.."

IMAGE="${SEMGREP_IMAGE:-semgrep/semgrep:1.184.0}"
CA_MOUNT=""
if [ -n "${MATRIZ_EXTRA_CA_CERT:-}" ]; then
  CA_MOUNT="$(cd "$(dirname "$MATRIZ_EXTRA_CA_CERT")" && pwd)/$(basename "$MATRIZ_EXTRA_CA_CERT")"
fi

# The entrypoint adds the extra certificate (if mounted) to the bundle the
# registry download uses, then runs the scan.
exec docker run --rm \
  -v "$PWD:/src" -w /src \
  ${CA_MOUNT:+-v "$CA_MOUNT:/tmp/extra-ca.pem:ro"} \
  -e SEMGREP_SEND_METRICS=off \
  --entrypoint sh \
  "$IMAGE" -c '
    if [ -f /tmp/extra-ca.pem ]; then
      cat "$(python3 -c "import certifi; print(certifi.where())")" /tmp/extra-ca.pem > /tmp/ca.pem
      export REQUESTS_CA_BUNDLE=/tmp/ca.pem SSL_CERT_FILE=/tmp/ca.pem
    fi
    exec semgrep scan --metrics=off --disable-version-check \
      --config .semgrep.yml \
      --config p/default \
      --config p/java \
      --config p/typescript \
      --config p/react \
      --config p/secrets \
      --config p/dockerfile \
      --config p/docker-compose \
      --config p/nginx \
      "$@"' semgrep "$@"
