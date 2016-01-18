#!/bin/bash -e

echo "Stopping docker"

if hash systemctl 2>/dev/null; then
  sudo systemctl stop docker
else
  sudo service docker stop
fi