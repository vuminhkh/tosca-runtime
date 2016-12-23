This image is currently pushed to docker hub under the tag toscaruntime/proxy-base:latest.
It's used to create base image for proxy.
Attention, this image does not have toscaruntime library, you should use 

`proxy create --from=toscaruntime/proxy-base:latest` 

command to build a proxy from this image, or use the default toscaruntime/proxy:${toscaruntime.version} which also derives from this image