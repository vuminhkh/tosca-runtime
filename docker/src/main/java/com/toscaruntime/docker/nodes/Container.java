package com.toscaruntime.docker.nodes;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.NetworkSettings;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.toscaruntime.exception.deployment.execution.InvalidOperationExecutionException;
import com.toscaruntime.exception.deployment.execution.ProviderResourcesNotFoundException;
import com.toscaruntime.util.ArtifactExecutionUtil;
import com.toscaruntime.util.DockerStreamDecoder;
import com.toscaruntime.util.DockerUtil;

import tosca.nodes.Compute;

@SuppressWarnings("unchecked")
public class Container extends Compute {

    private static final Logger log = LoggerFactory.getLogger(Container.class);

    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("^(\\S+)=(.+)$");

    private DockerClient dockerClient;

    private String containerId;

    private String ipAddress;

    public static final String RECIPE_LOCATION = "/var/recipe";

    public static final String GENERATED_SCRIPT_PATH = "/.generated";

    private static final String RECIPE_GENERATED_SCRIPT_LOCATION = RECIPE_LOCATION + GENERATED_SCRIPT_PATH;

    /**
     * This is the bootstrap context network. Container must be connected by default to this network so they can see each other.
     */
    private String bootstrapNetworkId;

    private String bootstrapNetworkName;

    /**
     * Those are networks explicitly defined by user in the recipe
     */
    private Set<Network> networks;

    /**
     * Those are volumes explicitly defined by user in the recipe
     */
    private Set<Volume> volumes;

    private String dockerHostIP;

    @Override
    public void initialLoad() {
        super.initialLoad();
        this.containerId = getAttributeAsString("provider_resource_id");
        this.ipAddress = getAttributeAsString("ip_address");
        this.dockerHostIP = getAttributeAsString("public_ip_address");
    }

    public String getImageId() {
        return getMandatoryPropertyAsString("image_id");
    }

    public List<ExposedPort> getExposedPorts() {
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

    public Map<Integer, Integer> getPortsMapping() {
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

    private Set<Volume> createdVolumes = new HashSet<>();

    public synchronized void attachVolume(Volume newVolume) {
        createdVolumes.add(newVolume);
        if (createdVolumes.size() >= volumes.size()) {
            createdVolumes.stream().forEach(volume -> log.info("Container [{}] : Mounting volume [{}] to location [{}]", getId(), volume.getVolumeId(), volume.getLocation()));
            dockerClient.stopContainerCmd(containerId).exec();
            StartContainerCmd startContainerCmd = dockerClient.startContainerCmd(containerId);
            // Once all volumes have been created we can restart
            List<Bind> binds = createdVolumes.stream().map(volume ->
                    new Bind(volume.getVolumeId(), new com.github.dockerjava.api.model.Volume(volume.getLocation()))).collect(Collectors.toList()
            );
            HostConfig hostConfig = new HostConfig();
            hostConfig.setBinds(binds.toArray(new Bind[binds.size()]));
            startContainerCmd.withHostConfig(hostConfig);
            startContainerCmd.exec();
        }
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
                portBindings.bind(exposedPort, Ports.binding(mappedPort));
            }
        }
        String tag = getPropertyAsString("tag", "latest");
        log.info("Container [" + getId() + "] : Pulling image " + imageId);
        try {
            dockerClient.pullImageCmd(imageId).withTag(tag).exec(new PullImageResultCallback()).awaitCompletion();
        } catch (InterruptedException e) {
            throw new InvalidOperationExecutionException("Pull interrupted", e);
        }
        log.info("Container [" + getId() + "] : Pulled image " + imageId);
        CreateContainerCmd createContainerCmd = dockerClient.createContainerCmd(imageId + ":" + tag)
                .withStdinOpen(Boolean.parseBoolean(getPropertyAsString("interactive", "true")))
                .withName(DockerUtil.normalizeResourceName(config.getDeploymentName() + "_" + getId()))
                .withExposedPorts(exposedPorts.toArray(new ExposedPort[exposedPorts.size()]))
                .withPortBindings(portBindings);
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
        StartContainerCmd startContainerCmd = dockerClient.startContainerCmd(containerId);
        startContainerCmd.exec();
        if (StringUtils.isNotBlank(bootstrapNetworkId)) {
            log.info("Container [" + getId() + "] : Connecting container {} to network {}", containerId, bootstrapNetworkId);
            dockerClient.connectToNetworkCmd().withContainerId(containerId).withNetworkId(bootstrapNetworkId).exec();
            log.info("Container [" + getId() + "] : Connected container {} to network {}", containerId, bootstrapNetworkId);
        }
        if (networks != null && !networks.isEmpty()) {
            for (Network network : networks) {
                log.info("Container [" + getId() + "] : Connecting container {} to network {}", containerId, network.getId());
                dockerClient.connectToNetworkCmd().withContainerId(containerId).withNetworkId(network.getNetworkId()).exec();
                log.info("Container [" + getId() + "] : Connected container {} to network {}", containerId, network.getId());
            }
        }
        InspectContainerResponse response = dockerClient.inspectContainerCmd(containerId).exec();
        if (response.getNetworkSettings().getNetworks() == null || response.getNetworkSettings().getNetworks().isEmpty()) {
            // Old version of docker will provide this
            ipAddress = response.getNetworkSettings().getIpAddress();
        } else {
            // Newer version with network API
            if (StringUtils.isNotBlank(bootstrapNetworkName) && response.getNetworkSettings().getNetworks().containsKey(bootstrapNetworkName)) {
                ipAddress = response.getNetworkSettings().getNetworks().get(bootstrapNetworkName).getIpAddress();
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
        setAttribute("public_ip_address", dockerHostIP);
        setAttribute("provider_resource_id", containerId);
        setAttribute("provider_resource_name", response.getName());
        log.info("Container [" + getId() + "] : Started container with id " + containerId + " and ip address " + ipAddress);
        DockerUtil.runCommand(dockerClient, containerId, "Container [" + getId() + "][Create recipe dir]", Lists.newArrayList("mkdir", "-p", RECIPE_LOCATION), log);
        dockerClient.copyArchiveToContainerCmd(containerId).withHostResource(this.config.getArtifactsPath().toString()).withDirChildrenOnly(true).withRemotePath(RECIPE_LOCATION).exec();
    }

    @Override
    public void stop() {
        super.stop();
        if (containerId == null) {
            throw new ProviderResourcesNotFoundException("Container has not been created yet");
        }
        log.info("Container [" + getId() + "] : Stopping container with id " + containerId);
        dockerClient.stopContainerCmd(containerId).exec();
        log.info("Container [" + getId() + "] : Stopped container with id " + containerId + " and ip address " + ipAddress);
        removeAttribute("ip_address");
        ipAddress = null;
    }

    @Override
    public void delete() {
        super.delete();
        if (containerId == null) {
            throw new ProviderResourcesNotFoundException("Container has not been created yet");
        }
        log.info("Container [" + getId() + "] : Deleting container with id " + containerId);
        dockerClient.removeContainerCmd(containerId).exec();
        log.info("Container [" + getId() + "] : Deleted container with id " + containerId);
        containerId = null;
        volumes.stream().filter(volume -> volume instanceof DeletableVolume).forEach(volume -> {
            dockerClient.removeVolumeCmd(volume.getVolumeId()).exec();
            log.info("Container [" + getId() + "] : Deleted volume with id " + volume.getVolumeId());
        });
    }

    @Override
    public Map<String, String> execute(String nodeId, String operationArtifactPath, Map<String, Object> environmentVariables, Map<String, String> deploymentArtifacts) {
        log.info("Container [{}] : Executing script [{}] for node [{}] with env [{}]", getId(), operationArtifactPath, nodeId, environmentVariables);
        String containerGeneratedScriptDir = Paths.get(RECIPE_GENERATED_SCRIPT_LOCATION + "/" + getId() + "/" + operationArtifactPath).getParent().toString();
        String containerScriptPath = RECIPE_LOCATION + "/" + operationArtifactPath;
        PrintWriter localGeneratedScriptWriter = null;
        String operationName = Paths.get(operationArtifactPath).getFileName().toString();
        try {
            Path localGeneratedScriptPath = Files.createTempFile("", ".sh");
            final String endOfOutputToken = UUID.randomUUID().toString();
            localGeneratedScriptWriter = new PrintWriter(new BufferedOutputStream(Files.newOutputStream(localGeneratedScriptPath)));
            localGeneratedScriptWriter.write("#!/bin/bash\n");
            Map<String, String> allInputs = ArtifactExecutionUtil.processInputs(environmentVariables, deploymentArtifacts, RECIPE_LOCATION, "/");
            for (Map.Entry<String, String> inputEntry : allInputs.entrySet()) {
                localGeneratedScriptWriter.write("export " + inputEntry.getKey() + "='" + inputEntry.getValue() + "'\n");
            }
            localGeneratedScriptWriter.write("chmod +x " + containerScriptPath + "\n");
            localGeneratedScriptWriter.write(". " + containerScriptPath + "\n");
            localGeneratedScriptWriter.write("_toscaruntime_rc=$?; if [[ $_toscaruntime_rc != 0 ]]; then echo \"Script exit with status $_toscaruntime_rc\"; exit $_toscaruntime_rc; fi" + "\n");
            localGeneratedScriptWriter.write("echo '" + endOfOutputToken + "'\n");
            localGeneratedScriptWriter.write("printenv\n");
            localGeneratedScriptWriter.flush();
            DockerUtil.runCommand(dockerClient, containerId, "Container [" + getId() + "][Create wrapper script dir]", Lists.newArrayList("mkdir", "-p", containerGeneratedScriptDir), log);
            dockerClient.copyArchiveToContainerCmd(containerId).withHostResource(localGeneratedScriptPath.toString()).withRemotePath(containerGeneratedScriptDir).exec();
            String copiedScript = containerGeneratedScriptDir + "/" + localGeneratedScriptPath.getFileName().toString();
            DockerUtil.runCommand(dockerClient, containerId, "Container [" + getId() + "][Chmod wrapper script]", Lists.newArrayList("chmod", "+x", copiedScript), log);
            Map<String, String> envVars = new HashMap<>();
            DockerUtil.runCommand(dockerClient, containerId, Lists.newArrayList(copiedScript), new DockerUtil.CommandLogger() {
                private boolean endOfOutputDetected = false;

                @Override
                public void log(DockerStreamDecoder.DecoderResult line) {
                    if (endOfOutputToken.equals(line.getData())) {
                        endOfOutputDetected = true;
                    } else {
                        if (endOfOutputDetected) {
                            Matcher matcher = ENV_VAR_PATTERN.matcher(line.getData());
                            if (matcher.matches()) {
                                envVars.put(matcher.group(1), matcher.group(2));
                            }
                        } else {
                            log.info("[{}][{}][{}] {}", nodeId, operationName, line.getStreamType().toString().toLowerCase(), line.getData());
                        }
                    }
                }
            });
            return envVars;
        } catch (IOException e) {
            throw new InvalidOperationExecutionException("Unable to create generated script for " + operationArtifactPath, e);
        } finally {
            IOUtils.closeQuietly(localGeneratedScriptWriter);
        }
    }

    public void setDockerClient(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public DockerClient getDockerClient() {
        return dockerClient;
    }

    public void setBootstrapNetworkId(String bootstrapNetworkId) {
        this.bootstrapNetworkId = bootstrapNetworkId;
    }

    public void setBootstrapNetworkName(String bootstrapNetworkName) {
        this.bootstrapNetworkName = bootstrapNetworkName;
    }

    public String getContainerId() {
        return containerId;
    }

    public void setNetworks(Set<Network> networks) {
        this.networks = networks;
    }

    public void setDockerHostIP(String dockerHostIP) {
        this.dockerHostIP = dockerHostIP;
    }

    public void setVolumes(Set<Volume> volumes) {
        this.volumes = volumes;
    }
}
