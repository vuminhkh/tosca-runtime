FROM phusion/baseimage

# Install Ansible
RUN \
    add-apt-repository ppa:ansible/ansible && \
    apt-get update && \
    apt-get install -y ansible

# Install docker
RUN curl -sSL https://get.docker.com/ | sh

RUN apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*