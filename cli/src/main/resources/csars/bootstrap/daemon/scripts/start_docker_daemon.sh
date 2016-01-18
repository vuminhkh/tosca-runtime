#!/bin/bash -e

echo "Starting docker"
if hash systemctl 2>/dev/null; then
  sudo systemctl start docker
else
  sudo service docker start
fi