package com.toscaruntime.common.nodes;

public abstract class DockerContainer<T> extends Compute {

    /**
     * Get the container id on which the micro manager can communicate
     *
     * @return the container's id
     */
    public abstract String getContainerId();

    /**
     * Get the docker client that can be used to communicate with the container
     *
     * @return the docker client
     */
    public abstract T getDockerClient();
}
