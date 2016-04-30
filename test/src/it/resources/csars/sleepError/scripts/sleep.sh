#!/bin/bash

if [ "$SLEEP_TIME" -gt "0" ]; then
  echo "Sleep $SLEEP_TIME seconds"
  sleep $SLEEP_TIME
else
  echo "Sleep time not configured, finish immediately"
fi