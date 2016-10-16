FROM ubuntu:trusty

RUN \
  apt-get update && \
  apt-get install -y -q curl wget zip vim apparmor python2.7 python2.7-dev

RUN ln -s /usr/bin/python2.7 /usr/bin/python

RUN apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*