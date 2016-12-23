This image is currently pushed to docker hub under the tag toscaruntime/deployer-base:latest.
It's used to create base image for deployer.
Attention, this image does not have toscaruntime library, you should use 

`deployer create --from=toscaruntime/deployer-base:latest` 

command to build a deployer from this image, or use the default toscaruntime/deployer:${toscaruntime.version} which also derives from this image