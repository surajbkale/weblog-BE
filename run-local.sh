#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# run-local.sh  —  Load .env then start the Spring Boot app with the local profile
#
# Usage:
#   ./run-local.sh          → run the app (skip tests)
#   ./run-local.sh test     → run the app + tests
# ─────────────────────────────────────────────────────────────────────────────

set -e  # exit on any error

ENV_FILE=".env"

if [ ! -f "$ENV_FILE" ]; then
  echo "❌  .env file not found. Copy .env.example and fill in your values:"
  echo "    cp .env.example .env"
  exit 1
fi

# Export all non-comment, non-empty lines from .env into the current shell
echo "📦  Loading environment from $ENV_FILE ..."
set -a   # automatically export all variables
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

echo "✅  Environment loaded."
echo ""

# Determine whether to skip tests
SKIP_TESTS="-DskipTests"
if [ "${1}" = "test" ]; then
  SKIP_TESTS=""
  echo "🧪  Running with tests enabled."
fi

echo "🚀  Starting Spring Boot (profile: local) ..."
echo ""

./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=local \
  $SKIP_TESTS
