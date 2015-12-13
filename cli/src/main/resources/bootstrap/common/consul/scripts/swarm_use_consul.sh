#!/bin/bash -e

echo "Start swarm manager on port ${SWARM_PORT} to manage consul at ${CONSUL_CLIENT_ADDRESS}"

sudo docker run -d -p ${SWARM_PORT}:${SWARM_PORT} swarm manage \
  -H tcp://0.0.0.0:${SWARM_PORT} \
  consul://${CONSUL_CLIENT_ADDRESS}/toscaruntime
