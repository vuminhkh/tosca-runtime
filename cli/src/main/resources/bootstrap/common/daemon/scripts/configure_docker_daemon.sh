#!/bin/bash -e

echo "Configuring docker on port ${DAEMON_PORT}"
. /etc/default/docker
echo "DOCKER_OPTS=\"${DOCKER_OPTS} -H tcp://0.0.0.0:2376 -H unix:///var/run/docker.sock\"" | sudo tee /etc/default/docker