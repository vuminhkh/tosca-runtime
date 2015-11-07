#!/bin/bash

echo "Installing docker on Ubuntu Trusty"

sudo apt-key adv --keyserver hkp://pgp.mit.edu:80 --recv-keys 58118E89F3A912897C070ADBF76221572C52609D

sudo touch /etc/apt/sources.list.d/docker.list

# TODO Add support for more systems based on operating system
# Ubuntu Precise
# deb https://apt.dockerproject.org/repo ubuntu-precise main
# Ubuntu Trusty
# deb https://apt.dockerproject.org/repo ubuntu-trusty main
# Ubuntu Vivid
# deb https://apt.dockerproject.org/repo ubuntu-vivid main
# Ubuntu Wily
# deb https://apt.dockerproject.org/repo ubuntu-wily main

echo "deb https://apt.dockerproject.org/repo ubuntu-trusty main" | sudo tee /etc/apt/sources.list.d/docker.list

sudo apt-get update

sudo apt-get purge lxc-docker*

sudo apt-cache policy docker-engine

sudo apt-get update

sudo apt-get install -y -q docker-engine

sudo service docker stop