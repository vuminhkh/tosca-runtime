#!/bin/bash -e

echo "Starting docker"
if hash systemctl 2>/dev/null; then
  sudo systemctl start docker
else
  sudo service docker start
fi

for IMAGE in $(echo $PULL_IMAGES | sed "s/,/ /g")
do
  echo "Pulling image $IMAGE"
  sudo docker pull $IMAGE
done