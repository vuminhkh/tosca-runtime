FROM phusion/baseimage

RUN rm -f /etc/service/sshd/down
RUN /etc/my_init.d/00_regen_ssh_host_keys.sh
RUN \
    apt-get update && \
    apt-get install -y python2.7 && \
    apt-get install -y python2.7-dev
RUN ln -s /usr/bin/python2.7 /usr/bin/python
CMD /sbin/my_init --enable-insecure-key