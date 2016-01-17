#!/bin/bash -e

echo "Test execute script on a docker container with args ${HELLO_ARGS}"

ls -la

export OUTPUT_TEST="Hello ${HELLO_ARGS}"