#!/bin/bash -e

echo "Configure docker daemon to publish proxy url ${PUBLIC_PROXY_URL} and ${PROXY_URL}"
. /etc/default/docker
echo "DOCKER_OPTS=\"${DOCKER_OPTS} --label com.toscaruntime.publicProxyURL=${PUBLIC_PROXY_URL} --label com.toscaruntime.proxyURL=${PROXY_URL}\"" | sudo tee /etc/default/docker