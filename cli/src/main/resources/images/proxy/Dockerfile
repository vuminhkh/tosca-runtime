FROM ${proxy.baseImage}
LABEL organization=toscaruntime.com
LABEL com.toscaruntime.imageType=proxy
WORKDIR /opt/docker
ADD . /opt/docker/
RUN chown -R toscaruntime:toscaruntime .
USER toscaruntime
ENTRYPOINT bin/toscaruntime-proxy