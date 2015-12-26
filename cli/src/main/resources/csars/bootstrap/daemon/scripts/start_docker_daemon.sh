#!/bin/bash -e

echo "Starting docker"
if [ -d "/usr/lib/systemd" ]; then
  sudo systemctl start docker
else
  sudo service docker start
fi