package com.mkv.tosca.docker;

import java.nio.file.Path;
import java.util.Map;

import tosca.nodes.Root;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DockerClientBuilder;
import com.mkv.exception.NonRecoverableException;
import com.mkv.tosca.docker.nodes.Container;

/**
 * This represents a docker deployment which must hold a docker client and inject this instance in all container in order to process the execution of workflows
 * 
 * @author Minh Khang VU
 */
public abstract class Deployment extends com.mkv.tosca.sdk.Deployment {

    private DockerClient dockerClient;

    @Override
    public void initializeDeployment(Path generatedRecipe, Map<String, String> inputs) {
        super.initializeDeployment(generatedRecipe, inputs);
        String daemonUrl = inputs.get("daemon_url");
        if (daemonUrl == null) {
            throw new NonRecoverableException("Daemon URL is mandatory to start docker deployment");
        }
        this.dockerClient = DockerClientBuilder.getInstance(daemonUrl).build();
    }

    @Override
    protected void initializeInstance(Root instance) {
        super.initializeInstance(instance);
        if (instance instanceof Container) {
            ((Container) instance).setDockerClient(dockerClient);
        }
    }
}
