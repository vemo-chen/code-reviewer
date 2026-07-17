#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
DOCKERFILE_PATH="${ROOT_DIR}/deploy/Dockerfile.backend"
OUTPUT_DIR="${OUTPUT_DIR:-${ROOT_DIR}/dist-images}"
IMAGE_REPO="${IMAGE_REPO:-chcnav/code-reviewer-backend}"
MAVEN_CMD="${MAVEN_CMD:-}"
JAR_PATH="${ROOT_DIR}/target/code-reviewer-0.0.1-SNAPSHOT.jar"
IMAGE_TAG="${IMAGE_TAG:-$(date +%Y%m%d%H%M)}"
DOCKER_PLATFORM="${DOCKER_PLATFORM:-}"

IMAGE_NAME="${IMAGE_REPO}:${IMAGE_TAG}"
OUTPUT_FILE="${OUTPUT_DIR}/code-reviewer-backend-${IMAGE_TAG}.tar"

if [[ -z "${MAVEN_CMD}" ]]; then
  if [[ -x "${ROOT_DIR}/mvnw" ]]; then
    MAVEN_CMD="${ROOT_DIR}/mvnw"
  elif command -v mvn >/dev/null 2>&1; then
    MAVEN_CMD="mvn"
  elif [[ -x "/Users/vemo/maven/apache-maven-3.9.16/bin/mvn" ]]; then
    MAVEN_CMD="/Users/vemo/maven/apache-maven-3.9.16/bin/mvn"
  else
    MAVEN_CMD=""
  fi
fi

mkdir -p "${OUTPUT_DIR}"

echo "[1/3] Build backend jar locally..."
cd "${ROOT_DIR}"
if [[ -n "${MAVEN_CMD}" ]]; then
  "${MAVEN_CMD}" -Dmaven.test.skip=true clean package
else
  mkdir -p "${HOME}/.m2"
  docker run --rm \
    --user "$(id -u):$(id -g)" \
    -v "${ROOT_DIR}:/workspace" \
    -v "${HOME}/.m2:/tmp/.m2" \
    -w /workspace \
    -e MAVEN_CONFIG=/tmp/.m2 \
    maven:3.9-eclipse-temurin-8 \
    mvn -Dmaven.repo.local=/tmp/.m2/repository -Dmaven.test.skip=true clean package
fi

if [[ ! -f "${JAR_PATH}" ]]; then
  echo
  echo "Backend jar not found: ${JAR_PATH}" >&2
  exit 1
fi

echo "[2/3] Build backend image ${IMAGE_NAME} ..."
docker build ${DOCKER_PLATFORM:+--platform "${DOCKER_PLATFORM}"} -f "${DOCKERFILE_PATH}" -t "${IMAGE_NAME}" "${ROOT_DIR}"

echo "[3/3] Export backend image to ${OUTPUT_FILE} ..."
docker save -o "${OUTPUT_FILE}" "${IMAGE_NAME}"

echo
echo "Build completed."
echo "Dockerfile: ${DOCKERFILE_PATH}"
echo "Image     : ${IMAGE_NAME}"
echo "File      : ${OUTPUT_FILE}"
