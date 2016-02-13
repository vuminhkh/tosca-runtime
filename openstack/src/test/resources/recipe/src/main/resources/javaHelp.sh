#!/bin/bash -e

sudo apt-get update
export JAVA_HELP=$(/usr/bin/java -h 2>&1)