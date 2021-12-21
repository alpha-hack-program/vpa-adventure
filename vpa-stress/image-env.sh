#!/bin/sh

export ARTIFACT_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
export ARTIFACT_ID=$(mvn help:evaluate -Dexpression=project.artifactId -q -DforceStdout)
export GIT_HASH=$(git rev-parse HEAD)

export REGISTRY=quay.io
export REGISTRY_USER_ID=atarazana
export PROJECT_ID=vpa
export APP_NAME=${PROJECT_ID}-${ARTIFACT_ID}-app

export BASE_VERSION=0.0.1
export BASE_IMAGE=${PROJECT_ID}-${ARTIFACT_ID}-base:${BASE_VERSION}

