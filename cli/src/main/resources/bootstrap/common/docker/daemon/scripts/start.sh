#!/bin/bash

echo "Starting docker"

sudo nohup docker daemon \
  -H tcp://0.0.0.0:2376 \
  -H unix:///var/run/docker.sock\
  --label com.toscaruntime.proxyURL="${PROXY_URL}" > /var/log/docker.log 2>&1 &