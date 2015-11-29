package com.toscaruntime.docker;

import java.util.Map;
import java.util.Set;

import com.github.dockerjava.api.DockerClient;
import com.toscaruntime.docker.nodes.Container;
import com.toscaruntime.sdk.Deployment;
import com.toscaruntime.sdk.DeploymentPostConstructor;
import com.toscaruntime.util.DockerUtil;

public class DockerDeploymentPostConstructor implements DeploymentPostConstructor {

    @Override
    public void postConstruct(Deployment deployment, Map<String, String> providerProperties, Map<String, Object> bootstrapContext) {
        DockerClient dockerClient = DockerUtil.buildDockerClient(providerProperties);
        Set<Container> containers = deployment.getNodeInstancesByType(Container.class);
        for (Container container : containers) {
            container.setDockerClient(dockerClient);
        }
    }
}
