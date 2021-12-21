#!/bin/sh

. ./image-env.sh

docker tag ${BASE_IMAGE} $REGISTRY/$REGISTRY_USER_ID/${BASE_IMAGE}

docker push $REGISTRY/$REGISTRY_USER_ID/${BASE_IMAGE}