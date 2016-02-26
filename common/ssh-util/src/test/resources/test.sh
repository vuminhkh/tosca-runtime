#!/bin/bash
set -e

log_file_path="/tmp/hss/log/mapr_zk_start.log"
exec > >(tee ${log_file_path}) 2>&1

echo "**** START ZOOKEEPER"

service mapr-zookeeper start

echo "END ZOOKEEPER"


exit 1
