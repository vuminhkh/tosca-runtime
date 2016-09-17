#!/usr/bin/env bash

echo "I'm a good scrip that finish well"

if [ -z "$MY_VARIABLE" ]; then
    echo "MY_VARIABLE is not set"
    exit 1
else
    echo "MY_VARIABLE is ${MY_VARIABLE}"
fi