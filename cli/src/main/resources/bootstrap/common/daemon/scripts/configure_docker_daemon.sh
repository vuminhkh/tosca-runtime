#!/bin/bash -e

echo "Configuring docker on port ${DAEMON_PORT}"

echo "DOCKER_OPTS=\"\${DOCKER_OPTS} -H tcp://0.0.0.0:${DAEMON_PORT} -H unix:///var/run/docker.sock\"" | sudo tee -a /etc/default/docker