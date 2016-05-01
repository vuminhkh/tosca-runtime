#!/bin/bash -e

echo "Installing docker"
if hash apt-get 2>/dev/null; then
  NAME="Docker Daemon creation"
  LOCK="/tmp/lockaptget"
  while true; do
    if mkdir "${LOCK}" &>/dev/null; then
      echo "$NAME take apt lock"
      break;
    fi
    echo "$NAME waiting apt lock to be released..."
    sleep 2
  done

  while sudo fuser /var/lib/dpkg/lock >/dev/null 2>&1 ; do
    echo "$NAME waiting for other software managers to finish..."
    sleep 2
  done

  curl -sSL https://get.docker.com/ | sudo sh

  rm -rf "${LOCK}"
  echo "$NAME released apt lock"
else
  curl -sSL https://get.docker.com/ | sudo sh
fi

if hash systemctl 2>/dev/null; then
  sudo systemctl stop docker
  sudo mkdir /etc/systemd/system/docker.service.d
  echo -e "[Service]\n""EnvironmentFile=-/etc/default/docker\n""ExecStart=\n""ExecStart=/usr/bin/docker daemon \$DOCKER_OPTS -H fd://\n" | sudo tee -a /etc/systemd/system/docker.service.d/docker.conf
  sudo systemctl daemon-reload
  sudo systemctl show docker --property EnvironmentFile
  sudo systemctl show docker --property ExecStart
else
  sudo service docker stop
fi
