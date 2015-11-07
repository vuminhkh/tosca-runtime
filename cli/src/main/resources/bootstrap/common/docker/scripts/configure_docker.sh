#!/bin/bash

echo "DOCKER_OPTS='-H tcp://0.0.0.0:2376 -H unix:///var/run/docker.sock'" | sudo tee /etc/default/docker