package com.mkv.tosca.docker;

import java.nio.file.Path;
import java.util.Properties;

import tosca.nodes.Root;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.mkv.tosca.docker.nodes.Container;

/**
 * This represents a docker deployment which must hold a docker client and inject this instance in all container in order to process the execution of workflows
 * 
 * @author Minh Khang VU
 */
public abstract class Deployment extends com.mkv.tosca.sdk.Deployment {

    private DockerClient dockerClient;

    @Override
    public void initializeDeployment(Path generatedRecipe, Properties inputs) {
        super.initializeDeployment(generatedRecipe, inputs);
        System.setProperty("http.maxConnections", String.valueOf(Integer.MAX_VALUE));
        DockerClientConfig config = new DockerClientConfig.DockerClientConfigBuilder().withProperties(inputs).build();
        this.dockerClient = DockerClientBuilder.getInstance(config).build();
    }

    @Override
    protected void initializeInstance(Root instance) {
        super.initializeInstance(instance);
        if (instance instanceof Container) {
            ((Container) instance).setDockerClient(dockerClient);
        }
    }
}
