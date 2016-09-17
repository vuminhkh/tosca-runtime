#!/bin/bash -e

echo "Test write this text $FILE_CONTENT to volume"

echo $FILE_CONTENT > /var/toscaruntimeTestVolume/test.txt

export WRITTEN=$(cat /var/toscaruntimeTestVolume/test.txt)