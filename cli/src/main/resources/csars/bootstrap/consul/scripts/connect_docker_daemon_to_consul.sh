#!/bin/bash -e

echo "Connect ${DOCKER_ADDRESS} to consul at ${CONSUL_CLIENT_ADDRESS}"

CONSUL_CLIENT_URL=consul://${CONSUL_CLIENT_ADDRESS}/toscaruntime

sudo service docker stop
. /etc/default/docker
echo "DOCKER_OPTS=\"${DOCKER_OPTS} --cluster-store ${CONSUL_CLIENT_URL} --cluster-advertise ${DOCKER_ADDRESS}\"" | sudo tee /etc/default/docker

sudo service docker start

sleep 5

sudo docker run -d swarm join --advertise=${DOCKER_ADDRESS} ${CONSUL_CLIENT_URL}
