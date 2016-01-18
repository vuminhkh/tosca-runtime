#!/usr/bin/env bash
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BASE_DIR="$(dirname "$SCRIPT_DIR")"
echo "Setting tosca runtime base dir to ${BASE_DIR}"
TOSCA_RUNTIME_OPTS="-Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled -Dtoscaruntime.clientMode=true -Dtoscaruntime.basedir=${BASE_DIR} ${TOSCA_RUNTIME_OPTS}"
java ${TOSCA_RUNTIME_OPTS} -jar $SCRIPT_DIR/sbt-launch.jar "@${BASE_DIR}/conf/launchConfig" "$@"