#!/bin/bash -e

CONSUL_TMP_ZIP=/tmp/consul.zip

curl -Lo ${CONSUL_TMP_ZIP} -O ${CONSUL_DOWNLOAD_URL}

echo "Downloaded consul binary from ${CONSUL_DOWNLOAD_URL} to temporary destination ${CONSUL_TMP_ZIP}"

if ! type "unzip" > /dev/null; then
  echo "Install unzip..."
  sudo apt-get update
  sudo apt-get install unzip
fi

sudo unzip -o ${CONSUL_TMP_ZIP} -d /usr/local/bin

echo "Unzipped consul package to /usr/local/bin"

rm ${CONSUL_TMP_ZIP}

if [ ! -d "$CONSUL_DATA_DIR" ]; then
  sudo mkdir ${CONSUL_DATA_DIR}
fi