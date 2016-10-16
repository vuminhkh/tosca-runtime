package com.toscaruntime.docker.nodes;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.NetworkSettings;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.toscaruntime.common.nodes.DockerContainer;
import com.toscaruntime.docker.DockerProviderConnection;
import com.toscaruntime.exception.deployment.execution.InvalidOperationExecutionException;
import com.toscaruntime.exception.deployment.execution.ProviderResourcesNotFoundException;
import com.toscaruntime.util.DockerUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
public class Container extends DockerContainer {

    private static final Logger log = LoggerFactory.getLogger(Container.class);

    private String containerId;

    private String ipAddress;

    /**
     * Those are networks explicitly defined by user in the recipe
     */
    private Set<Network> networks;

    /**
     * Those are volumes explicitly defined by user in the recipe
     */
    private Set<Volume> volumes;

    /**
     * All information / API to talk to the provider can be found in this object
     */
    private DockerProviderConnection connection;

    @Override
    public void initialLoad() {
        super.initialLoad();
        this.containerId = getAttributeAsString("provider_resource_id");
        this.ipAddress = getAttributeAsString("ip_address");
    }

    private String getImageId() {
        return getMandatoryPropertyAsString("image_id");
    }

    List<ExposedPort> getExposedPorts() {
        List<Map<String, String>> rawExposedPorts = (List<Map<String, String>>) getProperty("exposed_ports");
        List<ExposedPort> exposedPorts = Lists.newArrayList();
        if (rawExposedPorts != null) {
            for (Map<String, String> rawExposedPortsEntry : rawExposedPorts) {
                int port = Integer.parseInt(rawExposedPortsEntry.get("port"));
                if ("udp".equals(rawExposedPortsEntry.get("protocol"))) {
                    exposedPorts.add(ExposedPort.udp(port));
                } else {
                    exposedPorts.add(ExposedPort.tcp(port));
                }
            }
        }
        return exposedPorts;
    }

    Map<Integer, Integer> getPortsMapping() {
        Map<Integer, Integer> mapping = new HashMap<>();
        List<Map<String, String>> portMappingsRaw = (List<Map<String, String>>) getProperty("port_mappings");
        if (portMappingsRaw == null) {
            return mapping;
        }
        for (Map<String, String> entry : portMappingsRaw) {
            mapping.put(Integer.parseInt(entry.get("from").trim()), Integer.parseInt(entry.get("to").trim()));
        }
        return mapping;
    }

    @Override
    public void create() {
        super.create();
        String imageId = getImageId();
        log.info("Container [" + getId() + "] : Creating container with image " + imageId);
        List<ExposedPort> exposedPorts = getExposedPorts();
        Map<Integer, Integer> portMappings = getPortsMapping();
        Ports portBindings = new Ports();
        for (ExposedPort exposedPort : exposedPorts) {
            Integer mappedPort = portMappings.get(exposedPort.getPort());
            if (mappedPort != null) {
                portBindings.bind(exposedPort, Ports.Binding.bindPort(mappedPort));
            }
        }
        String tag = getPropertyAsString("tag", "latest");
        log.info("Container [" + getId() + "] : Pulling image " + imageId);
        try {
            connection.getDockerClient().pullImageCmd(imageId).withTag(tag).exec(new PullImageResultCallback()).awaitCompletion();
        } catch (InterruptedException e) {
            throw new InvalidOperationExecutionException("Pull interrupted", e);
        }
        log.info("Container [" + getId() + "] : Pulled image " + imageId);
        CreateContainerCmd createContainerCmd = connection.getDockerClient().createContainerCmd(imageId + ":" + tag)
                .withStdinOpen(Boolean.parseBoolean(getPropertyAsString("interactive", "true")))
                .withName(DockerUtil.normalizeResourceName(config.getDeploymentName() + "_" + getId()))
                .withExposedPorts(exposedPorts.toArray(new ExposedPort[exposedPorts.size()]))
                .withPortBindings(portBindings);
        if (volumes != null && !volumes.isEmpty()) {
            createContainerCmd.withBinds(volumes.stream().map(volume -> new Bind(volume.getVolumeId(), new com.github.dockerjava.api.model.Volume(volume.getLocation()))).collect(Collectors.toList()));
        }
        List<String> commands = (List<String>) getProperty("commands");
        if (commands != null && !commands.isEmpty()) {
            createContainerCmd.withCmd(commands);
        }
        containerId = createContainerCmd.exec().getId();
        log.info("Container [" + getId() + "] : Created container with id " + containerId);
    }

    @Override
    public void start() {
        super.start();
        if (containerId == null) {
            throw new ProviderResourcesNotFoundException("Container [" + getId() + "] : Container has not been created yet");
        }
        log.info("Container [" + getId() + "] : Starting container with id " + containerId);
        StartContainerCmd startContainerCmd = connection.getDockerClient().startContainerCmd(containerId);
        startContainerCmd.exec();
        if (StringUtils.isNotBlank(connection.getDockerNetworkId())) {
            log.info("Container [" + getId() + "] : Connecting container {} to network {}", containerId, connection.getDockerNetworkId());
            connection.getDockerClient().connectToNetworkCmd().withContainerId(containerId).withNetworkId(connection.getDockerNetworkId()).exec();
            log.info("Container [" + getId() + "] : Connected container {} to network {}", containerId, connection.getDockerNetworkId());
        }
        if (networks != null && !networks.isEmpty()) {
            for (Network network : networks) {
                log.info("Container [" + getId() + "] : Connecting container {} to network {}", containerId, network.getId());
                connection.getDockerClient().connectToNetworkCmd().withContainerId(containerId).withNetworkId(network.getNetworkId()).exec();
                log.info("Container [" + getId() + "] : Connected container {} to network {}", containerId, network.getId());
            }
        }
        InspectContainerResponse response = connection.getDockerClient().inspectContainerCmd(containerId).exec();
        if (response.getNetworkSettings().getNetworks() == null || response.getNetworkSettings().getNetworks().isEmpty()) {
            // Old version of docker will provide this
            ipAddress = response.getNetworkSettings().getIpAddress();
        } else {
            // Newer version with network API
            if (StringUtils.isNotBlank(connection.getDockerNetworkName()) && response.getNetworkSettings().getNetworks().containsKey(connection.getDockerNetworkName())) {
                ipAddress = response.getNetworkSettings().getNetworks().get(connection.getDockerNetworkName()).getIpAddress();
            } else if (response.getNetworkSettings().getNetworks().containsKey("bridge")) {
                ipAddress = response.getNetworkSettings().getNetworks().get("bridge").getIpAddress();
            } else {
                ipAddress = response.getNetworkSettings().getNetworks().values().iterator().next().getIpAddress();
            }
        }
        Map<String, String> ipAddresses = Maps.newHashMap();
        for (Map.Entry<java.lang.String, NetworkSettings.Network> networkEntry : response.getNetworkSettings().getNetworks().entrySet()) {
            ipAddresses.put(networkEntry.getKey(), networkEntry.getValue().getIpAddress());
        }
        setAttribute("ip_addresses", ipAddresses);
        setAttribute("ip_address", ipAddress);
        String publicIPAddress = null;
        if (connection.getSwarmNodesIPsMappings() != null && !connection.getSwarmNodesIPsMappings().isEmpty() && response.getNode() != null) {
            String dockerHostIP = response.getNode().getIp();
            setAttribute("docker_host_ip_address", dockerHostIP);
            publicIPAddress = connection.getSwarmNodesIPsMappings().get(dockerHostIP);
            setAttribute("docker_host_public_ip_address", publicIPAddress);
        }
        if (StringUtils.isBlank(publicIPAddress)) {
            publicIPAddress = connection.getDockerDaemonIP();
        }
        setAttribute("docker_daemon_ip_address", connection.getDockerDaemonIP());
        setAttribute("public_ip_address", publicIPAddress);
        setAttribute("provider_resource_id", containerId);
        setAttribute("provider_resource_name", response.getName());
        log.info("Container [" + getId() + "] : Started container with id " + containerId + " and ip address " + ipAddress);
    }

    @Override
    public void stop() {
        super.stop();
        if (containerId == null) {
            throw new ProviderResourcesNotFoundException("Container has not been created yet");
        }
        log.info("Container [" + getId() + "] : Stopping container with id " + containerId);
        connection.getDockerClient().stopContainerCmd(containerId).exec();
        log.info("Container [" + getId() + "] : Stopped container with id " + containerId + " and ip address " + ipAddress);
        removeAttribute("ip_addresses");
        removeAttribute("ip_address");
        removeAttribute("public_ip_address");
        ipAddress = null;
    }

    @Override
    public void delete() {
        super.delete();
        if (containerId == null) {
            throw new ProviderResourcesNotFoundException("Container has not been created yet");
        }
        log.info("Container [" + getId() + "] : Deleting container with id " + containerId);
        connection.getDockerClient().removeContainerCmd(containerId).exec();
        log.info("Container [" + getId() + "] : Deleted container with id " + containerId);
        containerId = null;
    }

    public void setConnection(DockerProviderConnection connection) {
        this.connection = connection;
    }

    public DockerProviderConnection getConnection() {
        return connection;
    }

    @Override
    public String getDockerHost() {
        return connection.getDockerDaemonConfig().getHost();
    }

    @Override
    public String getDockerCertificatePath() {
        return connection.getDockerDaemonConfig().getCertPath();
    }

    @Override
    public String getTlsVerify() {
        return connection.getDockerDaemonConfig().getTlsVerify();
    }

    public String getContainerId() {
        return containerId;
    }

    public void setNetworks(Set<Network> networks) {
        this.networks = networks;
    }

    public void setVolumes(Set<Volume> volumes) {
        this.volumes = volumes;
    }
}
