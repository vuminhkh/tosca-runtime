package com.toscaruntime.cli

import com.github.dockerjava.api.DockerClient

/**
 * A holder for docker client
 *
 * @author Minh Khang VU
 */
class DockerClientHolder(var dockerClient: DockerClient)
