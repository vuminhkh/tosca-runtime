package com.toscaruntime.docker;

import java.net.UnknownHostException;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.toscaruntime.docker.nodes.Container;
import com.toscaruntime.docker.nodes.Network;
import com.toscaruntime.docker.nodes.Volume;
import com.toscaruntime.exception.deployment.creation.ProviderInitializationException;
import com.toscaruntime.sdk.AbstractProviderHook;
import com.toscaruntime.sdk.Deployment;
import com.toscaruntime.sdk.util.DeploymentUtil;
import com.toscaruntime.util.DockerUtil;

import tosca.nodes.Root;

public class DockerProviderHook extends AbstractProviderHook {

    private static final Logger log = LoggerFactory.getLogger(Container.class);

    private DockerClient dockerClient;

    private String dockerHostIP;

    private String dockerNetworkId;

    private String dockerNetworkName;

    @Override
    public void postConstruct(Deployment deployment, Map<String, String> providerProperties, Map<String, Object> bootstrapContext) {
        dockerClient = DockerUtil.buildDockerClient(providerProperties);
        try {
            dockerHostIP = DockerUtil.getDockerHostIP(providerProperties);
        } catch (UnknownHostException e) {
            throw new ProviderInitializationException("Unable to resolve docker host", e);
        }
        // This is the default network that the container must be connected to in order to be able to communicate with others
        // This property is set if docker daemon has been bootstrapped as a swarm cluster
        dockerNetworkId = (String) bootstrapContext.get("docker_network_id");
        dockerNetworkName = (String) bootstrapContext.get("docker_network_name");
        if (StringUtils.isNotBlank(dockerNetworkId)) {
            log.info("Docker overlay network id [" + dockerNetworkId + "] docker network name [" + dockerNetworkName + "]");
        } else {
            log.info("No overlay docker network detected, must be in non toscaruntime bootstrap context");
        }
    }

    @Override
    public void postConstructInstances(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances) {
        if (nodeInstances != null) {
            for (Container container : DeploymentUtil.getNodeInstancesByType(nodeInstances, Container.class)) {
                Set<Network> connectedNetworks = DeploymentUtil.getTargetInstancesOfRelationship(relationshipInstances, container.getId(), tosca.relationships.Network.class, Network.class);
                Set<Volume> attachedVolumes = DeploymentUtil.getSourceInstancesOfRelationship(relationshipInstances, container.getId(), tosca.relationships.AttachTo.class, Volume.class);
                container.setDockerClient(dockerClient);
                container.setBootstrapNetworkId(dockerNetworkId);
                container.setBootstrapNetworkName(dockerNetworkName);
                container.setNetworks(connectedNetworks);
                container.setDockerHostIP(dockerHostIP);
                container.setVolumes(attachedVolumes);
                attachedVolumes.stream().forEach(volume -> volume.setContainer(container));
            }
            for (Network network : DeploymentUtil.getNodeInstancesByType(nodeInstances, Network.class)) {
                network.setDockerClient(dockerClient);
            }
            for (Volume volume : DeploymentUtil.getNodeInstancesByType(nodeInstances, Volume.class)) {
                volume.setDockerClient(dockerClient);
            }
        }
    }
}
