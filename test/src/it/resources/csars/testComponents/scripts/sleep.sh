#!/bin/bash

OTHER_SLEEP_PROCESS_COUNT=$(ps -ef | grep sleep | grep -v grep | wc -l)
if [ "$OTHER_SLEEP_PROCESS_COUNT" -gt "0" ]; then
  echo "$OTHER_SLEEP_PROCESS_COUNT sleep process detected on the machine "
  exit 1
fi

if [ "$SLEEP_TIME" -gt "0" ]; then
  echo "Sleep $SLEEP_TIME seconds"
  sleep $SLEEP_TIME
else
  echo "Sleep time not configured, finish immediately"
fi