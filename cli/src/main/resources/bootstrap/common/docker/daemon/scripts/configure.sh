#!/bin/bash

echo "Configuring docker on port ${DAEMON_PORT} with proxy url ${PROXY_URL}"

echo "DOCKER_OPTS='-H tcp://0.0.0.0:${DAEMON_PORT} -H unix:///var/run/docker.sock --label com.toscaruntime.proxyURL=${PROXY_URL}'" | sudo tee /etc/default/docker