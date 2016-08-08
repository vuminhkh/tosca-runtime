#!/bin/bash -e

echo "Start consul agent in ${CONSUL_AGENT_MODE} mode, expecting ${CONSUL_SERVERS_COUNT} servers, data dir at ${CONSUL_DATA_DIR}, bind on interface ${CONSUL_BIND_ADDRESS}"

if [ "$CONSUL_AGENT_MODE" == "server" ]; then
  SERVER_MODE="-server"
  BOOTSTRAP_EXPECT="-bootstrap-expect ${CONSUL_SERVERS_COUNT}"
fi
sudo nohup consul agent ${SERVER_MODE} ${BOOTSTRAP_EXPECT} \
  -data-dir ${CONSUL_DATA_DIR} \
  -bind ${CONSUL_BIND_ADDRESS} \
  -ui \
  -client 0.0.0.0 >/tmp/consul.log 2>&1 &

# TODO Export the consul address by using consul members for later injection, for now we'll use default static address

echo "Consul has following members until now"

sleep 2

sudo consul members

export CONSUL_SERVER_ADDRESS=${CONSUL_BIND_ADDRESS}:8301
export CONSUL_CLIENT_ADDRESS=${CONSUL_BIND_ADDRESS}:8500