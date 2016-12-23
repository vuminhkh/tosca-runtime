FROM ${deployer.baseImage}
LABEL organization=toscaruntime.com
LABEL com.toscaruntime.imageType=deployer
WORKDIR /opt/docker
ADD . /opt/docker/
RUN chown -R toscaruntime:toscaruntime .
USER toscaruntime
ENTRYPOINT bin/toscaruntime-deployer