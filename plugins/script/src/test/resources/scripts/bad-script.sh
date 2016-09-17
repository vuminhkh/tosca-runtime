#!/usr/bin/env bash

echo "I'm a bad scrip that will fail"

if [ -z "$MY_VARIABLE" ]; then
    echo "MY_VARIABLE is not set"
else
    echo "MY_VARIABLE is ${MY_VARIABLE}"
    exit 1
fi