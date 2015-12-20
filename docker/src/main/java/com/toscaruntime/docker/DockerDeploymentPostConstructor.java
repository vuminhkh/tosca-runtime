package com.toscaruntime.docker;

import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.toscaruntime.docker.nodes.Container;
import com.toscaruntime.sdk.Deployment;
import com.toscaruntime.sdk.DeploymentPostConstructor;
import com.toscaruntime.util.DockerUtil;

public class DockerDeploymentPostConstructor implements DeploymentPostConstructor {

    private static final Logger log = LoggerFactory.getLogger(Container.class);

    @Override
    public void postConstruct(Deployment deployment, Map<String, String> providerProperties, Map<String, Object> bootstrapContext) {
        DockerClient dockerClient = DockerUtil.buildDockerClient(providerProperties);
        Set<Container> containers = deployment.getNodeInstancesByType(Container.class);
        String dockerNetworkId = (String) bootstrapContext.get("docker_network_id");
        String dockerNetworkName = (String) bootstrapContext.get("docker_network_name");
        if (StringUtils.isNotBlank(dockerNetworkId)) {
            log.info("Docker overlay network id " + dockerNetworkId + " docker network name " + dockerNetworkName);
        } else {
            log.info("No overlay docker network detected, must be in non toscaruntime bootstrap context");
        }
        for (Container container : containers) {
            container.setDockerClient(dockerClient);
            container.setNetworkId(dockerNetworkId);
            container.setNetworkName(dockerNetworkName);
        }
    }
}
