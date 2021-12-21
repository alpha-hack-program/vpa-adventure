#!/bin/sh

. ./image-env.sh

docker build -f src/main/docker/Dockerfile.jvm.base -t ${BASE_IMAGE} .

