#!/bin/bash -e

echo "Create + start docker container"
if [ -n "${DOCKER_PORTS}" ]; then
  DOCKER_PORTS="-p ${DOCKER_PORTS}";
fi
sudo docker run -e DOCKER_HOST=${DOCKER_HOST} -d ${DOCKER_PORTS} ${DOCKER_IMAGE} ${DOCKER_COMMAND}