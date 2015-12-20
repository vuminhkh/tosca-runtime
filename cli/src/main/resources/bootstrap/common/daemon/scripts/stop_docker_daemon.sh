#!/bin/bash -e

echo "Stopping docker"

if [ -d "/usr/lib/systemd" ]; then
  sudo systemctl stop docker
else
  sudo service docker stop
fi