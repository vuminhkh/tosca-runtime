#!/bin/bash -e

echo "Create + start docker container with image ${DOCKER_IMAGE} and labels ${DOCKER_LABELS}"
if [ -n "${DOCKER_PORTS}" ]; then
  DOCKER_PORTS_OPT="-p ${DOCKER_PORTS}"
fi
if [ -n "${DOCKER_CONTAINER_NAME}" ]; then
  DOCKER_CONTAINER_NAME_OPT="--name=${DOCKER_CONTAINER_NAME}"
fi
CREATED_CONTAINER=$(sudo docker run -d ${DOCKER_CONTAINER_NAME_OPT} \
  -e DOCKER_URL=${DOCKER_URL} \
  -e PUBLIC_DOCKER_URL=${PUBLIC_DOCKER_URL} \
  ${DOCKER_LABELS} ${DOCKER_PORTS_OPT} ${DOCKER_IMAGE} ${DOCKER_COMMAND})

if [ -n "${DOCKER_NETWORK_ID}" ]; then
  sudo docker network connect ${DOCKER_NETWORK_ID} ${CREATED_CONTAINER}
fi