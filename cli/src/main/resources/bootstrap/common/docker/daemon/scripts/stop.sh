#!/bin/bash

echo "Stopping docker"

sudo ps -ef | grep "docker daemon" | grep -v grep | awk '{print $2}' | sudo xargs kill