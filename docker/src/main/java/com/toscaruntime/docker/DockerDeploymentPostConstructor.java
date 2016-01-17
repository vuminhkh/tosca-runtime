package com.toscaruntime.docker;

import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.toscaruntime.docker.nodes.Container;
import com.toscaruntime.docker.nodes.Network;
import com.toscaruntime.sdk.Deployment;
import com.toscaruntime.sdk.DeploymentPostConstructor;
import com.toscaruntime.util.DockerUtil;

public class DockerDeploymentPostConstructor implements DeploymentPostConstructor {

    private static final Logger log = LoggerFactory.getLogger(Container.class);

    @Override
    public void postConstruct(Deployment deployment, Map<String, String> providerProperties, Map<String, Object> bootstrapContext) {
        DockerClient dockerClient = DockerUtil.buildDockerClient(providerProperties);
        String dockerHostIP = DockerUtil.getDockerHostIP(providerProperties);
        Set<Container> containers = deployment.getNodeInstancesByType(Container.class);
        // This is the default network that the container must be connected to in order to be able to communicate with others
        // This property is set if docker daemon has been bootstrapped as a swarm cluster
        String dockerNetworkId = (String) bootstrapContext.get("docker_network_id");
        String dockerNetworkName = (String) bootstrapContext.get("docker_network_name");
        if (StringUtils.isNotBlank(dockerNetworkId)) {
            log.info("Docker overlay network id [" + dockerNetworkId + "] docker network name [" + dockerNetworkName + "]");
        } else {
            log.info("No overlay docker network detected, must be in non toscaruntime bootstrap context");
        }
        for (Container container : containers) {
            container.setDockerClient(dockerClient);
            container.setBootstrapNetworkId(dockerNetworkId);
            container.setBootstrapNetworkName(dockerNetworkName);
            Set<Network> connectedNetworks = deployment.getNodeInstancesByRelationship(container.getId(), tosca.relationships.Network.class, Network.class);
            container.setNetworks(connectedNetworks);
            container.setDockerHostIP(dockerHostIP);
        }
        Set<Network> networks = deployment.getNodeInstancesByType(Network.class);
        for (Network network : networks) {
            network.setDockerClient(dockerClient);
        }
    }
}
