FROM openjdk:8-jdk

RUN useradd -ms /bin/bash toscaruntime

RUN \
    apt-get update && \
    apt-get install -y build-essential libssl-dev libffi-dev python-pip python-dev python-yaml git

# Install Ansible
RUN pip install -U cffi PyYAML jinja2 paramiko

RUN git clone https://github.com/ansible/ansible.git

RUN cd ansible && make install

# Install docker
RUN curl -sSL https://get.docker.com/ | sh

RUN apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*