#!/bin/bash -e

echo "Connect ${DOCKER_ADDRESS} to consul at ${CONSUL_CLIENT_ADDRESS}"

sudo docker run -d swarm join --advertise=${DOCKER_ADDRESS} consul://${CONSUL_CLIENT_ADDRESS}/toscaruntime
