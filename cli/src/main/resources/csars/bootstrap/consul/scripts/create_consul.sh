#!/bin/bash -e

CONSUL_TMP_ZIP=/tmp/consul.zip

curl -Lo ${CONSUL_TMP_ZIP} -O ${CONSUL_DOWNLOAD_URL}

echo "Downloaded consul binary from ${CONSUL_DOWNLOAD_URL} to temporary destination ${CONSUL_TMP_ZIP}"

if ! type "unzip" > /dev/null; then
  echo "Need unzip to install consul..."
  if hash apt-get 2>/dev/null; then
    NAME="Unzip installation"
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

    sudo apt-get update
    sudo apt-get install -y -q unzip

    rm -rf "${LOCK}"
    echo "$NAME released apt lock"
  else
    sudo yum -y update
    sudo yum -y install unzip
  fi
fi

sudo unzip -o ${CONSUL_TMP_ZIP} -d /usr/local/bin

echo "Unzipped consul package to /usr/local/bin"

rm ${CONSUL_TMP_ZIP}

if [ ! -d "$CONSUL_DATA_DIR" ]; then
  sudo mkdir ${CONSUL_DATA_DIR}
fi
