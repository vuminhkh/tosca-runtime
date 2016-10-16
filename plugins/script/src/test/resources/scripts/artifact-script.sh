#!/usr/bin/env bash

if [ ! -f "$MY_CONF" ]; then
    echo "File not found $MY_CONF !"
    exit 1
else
    echo "File found "$(ls -la $MY_CONF)
fi