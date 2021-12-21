#!/bin/sh

. ./image-env.sh

./image-build-base.sh
docker tag ${BASE_IMAGE} ${REGISTRY}/${REGISTRY_USER_ID}/${BASE_IMAGE}

./mvnw clean package -DskipTests

docker build -f src/main/docker/Dockerfile.jvm -t ${PROJECT_ID}-${ARTIFACT_ID}:${GIT_HASH} --build-arg FROM_IMAGE="${REGISTRY}/${REGISTRY_USER_ID}/${BASE_IMAGE}" .

