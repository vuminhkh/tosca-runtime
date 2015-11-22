#!/bin/bash

echo "Installing docker"

curl -sSL https://get.docker.com/ | sudo sh

sudo ps -ef | grep "docker daemon" | grep -v grep | awk '{print $2}' | sudo xargs kill