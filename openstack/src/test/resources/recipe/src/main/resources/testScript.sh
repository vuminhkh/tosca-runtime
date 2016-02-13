#!/bin/bash -e

echo "Test execute script with args ${HELLO_ARGS}"

ls -la ./.toscaruntime/recipe

export OUTPUT_TEST="Hello ${HELLO_ARGS}"