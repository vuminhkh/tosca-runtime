#!/bin/bash -e

echo "Create + start docker container with image ${DOCKER_IMAGE} and labels ${DOCKER_LABELS}"
if [ -n "${DOCKER_PORTS}" ]; then
  DOCKER_PORTS="-p ${DOCKER_PORTS}";
fi
sudo docker run -d \
  -e DOCKER_URL=${DOCKER_URL} \
  -e PUBLIC_DOCKER_URL=${PUBLIC_DOCKER_URL} \
  ${DOCKER_LABELS} ${DOCKER_PORTS} ${DOCKER_IMAGE} ${DOCKER_COMMAND}