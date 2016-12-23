This image is currently pushed to docker hub under the tag toscaruntime/deployer-ansible-base:latest.
It's used to create base image for deployer with ansible and docker installed.
Attention, this image does not have toscaruntime library, you should use 

`deployer create --from=toscaruntime/deployer-ansible-base:latest` 

command to build a deployer from this image. 
This is useful to run ansible playbook with a local connection.