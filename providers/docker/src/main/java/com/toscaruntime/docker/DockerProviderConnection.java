package com.toscaruntime.docker;

import com.github.dockerjava.api.DockerClient;
import com.toscaruntime.docker.util.IpAddressUtil;
import com.toscaruntime.exception.deployment.creation.ProviderInitializationException;
import com.toscaruntime.util.DockerUtil;
import com.toscaruntime.util.PropertyUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.UnknownHostException;
import java.util.Map;

public class DockerProviderConnection {

    private static final Logger log = LoggerFactory.getLogger(DockerProviderConnection.class);

    private DockerClient dockerClient;

    private String dockerDaemonIP;

    private Map<String, String> swarmNodesIPsMappings;

    private String dockerNetworkId;

    private String dockerNetworkName;

    public DockerProviderConnection(Map<String, Object> pluginProperties, Map<String, Object> bootstrapContext) {
        Map<String, String> flattenProperties = PropertyUtil.flatten(pluginProperties);
        dockerClient = DockerUtil.buildDockerClient(flattenProperties);
        try {
            dockerDaemonIP = DockerUtil.getDockerDaemonIP(flattenProperties);
        } catch (UnknownHostException e) {
            throw new ProviderInitializationException("Unable to resolve docker host", e);
        }
        if (PropertyUtil.getMandatoryPropertyAsString(flattenProperties, DockerUtil.DOCKER_URL_KEY).equals(PropertyUtil.getPropertyAsString(bootstrapContext, "public_daemon_url"))) {
            // In a multiple target configuration then use information from bootstrap context only if the target concerns the bootstrapped daemon
            // Only available in swarm bootstrapped environment
            swarmNodesIPsMappings = IpAddressUtil.extractSwarmNodesIpsMappings(bootstrapContext);
            // This is the default network that the container must be connected to in order to be able to communicate with others
            // This property is set if docker daemon has been bootstrapped as a swarm cluster
            dockerNetworkId = (String) bootstrapContext.get("docker_network_id");
            dockerNetworkName = (String) bootstrapContext.get("docker_network_name");
            if (StringUtils.isNotBlank(dockerNetworkId)) {
                log.info("Docker overlay network id [" + dockerNetworkId + "] docker network name [" + dockerNetworkName + "]");
            } else {
                log.info("No overlay docker network detected, must be in non bootstrap context");
            }
        }
    }

    public DockerClient getDockerClient() {
        return dockerClient;
    }

    public String getDockerDaemonIP() {
        return dockerDaemonIP;
    }

    public Map<String, String> getSwarmNodesIPsMappings() {
        return swarmNodesIPsMappings;
    }

    public String getDockerNetworkId() {
        return dockerNetworkId;
    }

    public String getDockerNetworkName() {
        return dockerNetworkName;
    }
}
