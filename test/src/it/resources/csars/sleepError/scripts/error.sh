#!/bin/bash

if [ "$THROW_ERROR" == "true" ]; then
  echo "I'm in a bad mood, I'll throw some errors"
  exit 1
else
  echo "I'm happy, I won't throw any error"
fi