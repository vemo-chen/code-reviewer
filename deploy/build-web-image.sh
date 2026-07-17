#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
WEB_DIR="${ROOT_DIR}/web-ui"
DOCKERFILE_PATH="${ROOT_DIR}/deploy/Dockerfile.web"
OUTPUT_DIR="${OUTPUT_DIR:-${ROOT_DIR}/dist-images}"
IMAGE_REPO="${IMAGE_REPO:-chcnav/code-reviewer-web}"
IMAGE_TAG="${IMAGE_TAG:-$(date +%Y%m%d%H%M)}"
DOCKER_PLATFORM="${DOCKER_PLATFORM:-}"

IMAGE_NAME="${IMAGE_REPO}:${IMAGE_TAG}"
OUTPUT_FILE="${OUTPUT_DIR}/code-reviewer-web-${IMAGE_TAG}.tar"

mkdir -p "${OUTPUT_DIR}"

echo "[1/2] Build web image ${IMAGE_NAME} ..."
docker build ${DOCKER_PLATFORM:+--platform "${DOCKER_PLATFORM}"} -f "${DOCKERFILE_PATH}" -t "${IMAGE_NAME}" "${WEB_DIR}"

echo "[2/2] Export web image to ${OUTPUT_FILE} ..."
docker save -o "${OUTPUT_FILE}" "${IMAGE_NAME}"

echo
echo "Build completed."
echo "Dockerfile: ${DOCKERFILE_PATH}"
echo "Image     : ${IMAGE_NAME}"
echo "File      : ${OUTPUT_FILE}"
