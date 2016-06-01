#!/bin/bash -e

echo "Start swarm manager on port ${SWARM_PORT} to manage consul at ${CONSUL_CLIENT_ADDRESS}"

sudo docker run -d -p ${SWARM_PORT}:${SWARM_PORT} swarm:1.2.2 manage \
  -H tcp://0.0.0.0:${SWARM_PORT} \
  consul://${CONSUL_CLIENT_ADDRESS}/toscaruntime

sleep 5

export DOCKER_NETWORK_NAME=toscaruntime
export DOCKER_NETWORK_ID=$(sudo docker -H tcp://0.0.0.0:${SWARM_PORT} network create --driver overlay ${DOCKER_NETWORK_NAME})
