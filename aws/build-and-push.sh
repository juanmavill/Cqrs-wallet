#!/usr/bin/env bash
# Construye las 3 imágenes y las pushea a Docker Hub.
# Uso:
#   export DOCKERHUB_USER=tuusuario
#   docker login
#   ./build-and-push.sh

set -euo pipefail

: "${DOCKERHUB_USER:?DOCKERHUB_USER no está definido}"

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_DIR"

TAG="1.0.0"

for svc in command-service query-service monolith-reference; do
  IMAGE="${DOCKERHUB_USER}/cqrs-${svc}:${TAG}"
  echo ">>> Construyendo $IMAGE"
  docker build -f "${svc}/Dockerfile" -t "$IMAGE" .
  echo ">>> Pushing $IMAGE"
  docker push "$IMAGE"
done

echo ""
echo "Listo. Imágenes disponibles en:"
echo "  ${DOCKERHUB_USER}/cqrs-command-service:${TAG}"
echo "  ${DOCKERHUB_USER}/cqrs-query-service:${TAG}"
echo "  ${DOCKERHUB_USER}/cqrs-monolith-reference:${TAG}"
