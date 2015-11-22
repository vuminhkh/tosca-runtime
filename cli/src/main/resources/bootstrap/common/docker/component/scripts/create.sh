#!/bin/bash

echo "Create + start docker container"
if [ -n "${DOCKER_PORTS}" ]; then
  DOCKER_PORTS="-p ${DOCKER_PORTS}";
fi
sudo docker run ${DOCKER_PORTS} ${DOCKER_IMAGE} ${DOCKER_COMMAND}