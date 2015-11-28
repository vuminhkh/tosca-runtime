#!/bin/bash -e

echo "Installing docker"

curl -sSL https://get.docker.com/ | sudo sh

sudo service docker stop