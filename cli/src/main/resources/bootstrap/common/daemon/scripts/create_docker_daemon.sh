#!/bin/bash -e

echo "Installing docker"

curl -sSL https://get.docker.com/ | sudo sh

if [ -d "/usr/lib/systemd" ]; then
  sudo systemctl stop docker
  sudo mkdir /etc/systemd/system/docker.service.d
  echo -e "[Service]\n""EnvironmentFile=-/etc/default/docker\n""ExecStart=\n""ExecStart=/usr/bin/docker daemon \$DOCKER_OPTS -H fd://\n" | sudo tee -a /etc/systemd/system/docker.service.d/docker.conf
  sudo systemctl daemon-reload
  sudo systemctl show docker --property EnvironmentFile
  sudo systemctl show docker --property ExecStart
else
  sudo service docker stop
fi

