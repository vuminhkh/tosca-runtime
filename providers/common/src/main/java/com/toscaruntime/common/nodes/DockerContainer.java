package com.toscaruntime.common.nodes;

public abstract class DockerContainer extends Compute {

    /**
     * Get the container id on which the micro manager can communicate
     *
     * @return the container's id
     */
    public abstract String getContainerId();

    /**
     * Get the docker url that can be used to communicate with the container
     *
     * @return the docker url
     */
    public abstract String getDockerURL();


    /**
     * Get the docker certificate path that can be used to communicate with the container
     *
     * @return the docker certificate path
     */
    public abstract String getDockerCertificatePath();
}
