#!/usr/bin/env bash
# Builds the three service images and pushes them to Docker Hub.
# Usage:
#   export DOCKERHUB_USER=<username>
#   docker login
#   ./build-and-push.sh

set -euo pipefail

: "${DOCKERHUB_USER:?DOCKERHUB_USER is not set}"

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_DIR"

TAG="1.0.0"

for svc in command-service query-service monolith-reference; do
  IMAGE="${DOCKERHUB_USER}/cqrs-${svc}:${TAG}"
  echo "Building $IMAGE"
  docker build -f "${svc}/Dockerfile" -t "$IMAGE" .
  echo "Pushing $IMAGE"
  docker push "$IMAGE"
done

echo ""
echo "Images available at:"
echo "  ${DOCKERHUB_USER}/cqrs-command-service:${TAG}"
echo "  ${DOCKERHUB_USER}/cqrs-query-service:${TAG}"
echo "  ${DOCKERHUB_USER}/cqrs-monolith-reference:${TAG}"
