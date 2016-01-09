package com.toscaruntime.docker.nodes;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.toscaruntime.util.DockerStreamDecoder;
import com.toscaruntime.util.DockerUtil;
import com.toscaruntime.util.PropertyUtil;

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
                portBindings.bind(exposedPort, Ports.Binding(mappedPort));
            }
        }
        String tag = getPropertyAsString("tag", "latest");
        log.info("Container [" + getId() + "] : Pulling image " + imageId);
        dockerClient.pullImageCmd(imageId).withTag(tag).exec(new PullImageResultCallback()).awaitSuccess();
        log.info("Container [" + getId() + "] : Pulled image " + imageId);
        CreateContainerCmd createContainerCmd = dockerClient.createContainerCmd(imageId + ":" + tag)
                .withStdinOpen(Boolean.parseBoolean(getPropertyAsString("interactive", "true")))
                .withName(config.getDeploymentName().replaceAll("[^\\p{L}\\p{Nd}]+", "") + "_" + getId())
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
            throw new RuntimeException("Container [" + getId() + "] : Container has not been created yet");
        }
        log.info("Container [" + getId() + "] : Starting container with id " + containerId);
        dockerClient.startContainerCmd(containerId).exec();
        if (StringUtils.isNotBlank(bootstrapNetworkId)) {
            log.info("Container [" + getId() + "] : Connecting container {} to network {}", containerId, bootstrapNetworkId);
            dockerClient.connectContainerToNetworkCmd(containerId, bootstrapNetworkId).exec();
            log.info("Container [" + getId() + "] : Connected container {} to network {}", containerId, bootstrapNetworkId);
        }
        if (networks != null && !networks.isEmpty()) {
            for (Network network : networks) {
                log.info("Container [" + getId() + "] : Connecting container {} to network {}", containerId, network.getId());
                dockerClient.connectContainerToNetworkCmd(containerId, network.getNetworkId()).exec();
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
        for (Map.Entry<java.lang.String, InspectContainerResponse.Network> networkEntry : response.getNetworkSettings().getNetworks().entrySet()) {
            ipAddresses.put(networkEntry.getKey(), networkEntry.getValue().getIpAddress());
        }
        setAttribute("ip_addresses", ipAddresses);
        setAttribute("ip_address", ipAddress);
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
            throw new RuntimeException("Container has not been created yet");
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
            throw new RuntimeException("Container has not been created yet");
        }
        log.info("Container [" + getId() + "] : Deleting container with id " + containerId);
        dockerClient.removeContainerCmd(containerId).exec();
        log.info("Container [" + getId() + "] : Deleted container with id " + containerId);
        containerId = null;
    }

    /**
     * Use docker exec to run scripts inside docker container
     *
     * @param operationArtifactPath the relative path to the script in the recipe
     */
    public Map<String, String> execute(String nodeId, String operationArtifactPath, Map<String, Object> environmentVariables) {
        String containerGeneratedScriptDir = Paths.get(RECIPE_GENERATED_SCRIPT_LOCATION + "/" + getId() + "/" + operationArtifactPath).getParent().toString();
        String containerScriptPath = RECIPE_LOCATION + "/" + operationArtifactPath;
        PrintWriter localGeneratedScriptWriter = null;
        try {
            Path localGeneratedScriptPath = Files.createTempFile("", ".sh");
            final String endOfOutputToken = UUID.randomUUID().toString();
            Files.createDirectories(localGeneratedScriptPath.getParent());
            localGeneratedScriptWriter = new PrintWriter(new BufferedOutputStream(Files.newOutputStream(localGeneratedScriptPath)));
            localGeneratedScriptWriter.write("#!/bin/bash -e\n");
            if (environmentVariables != null) {
                for (Map.Entry<String, Object> envEntry : environmentVariables.entrySet()) {
                    String envValue = PropertyUtil.propertyValueToString(envEntry.getValue());
                    if (envValue != null) {
                        String escapedEnvValue = envValue.replace("'", "'\\''");
                        localGeneratedScriptWriter.write("export " + envEntry.getKey() + "='" + escapedEnvValue + "'\n");
                    }
                }
            }
            Map<String, String> allArtifacts = getHostDeploymentArtifacts();
            if (allArtifacts != null && !allArtifacts.isEmpty()) {
                for (Map.Entry<String, String> deploymentEntry : allArtifacts.entrySet()) {
                    if (deploymentEntry.getValue() != null) {
                        String escapedEnvValue = deploymentEntry.getValue().replace("'", "'\\''");
                        localGeneratedScriptWriter.write("export " + deploymentEntry.getKey() + "='" + RECIPE_LOCATION + "/" + escapedEnvValue + "'\n");
                    }
                }
            }
            localGeneratedScriptWriter.write("chmod +x " + containerScriptPath + "\n");
            localGeneratedScriptWriter.write(". " + containerScriptPath + "\n");
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
                            log.info("[{}][{}][{}] {}", getId(), operationArtifactPath, line.getStreamType(), line.getData());
                        }
                    }
                }
            });
            return envVars;
        } catch (IOException e) {
            throw new RuntimeException("Unable to create generated script for " + operationArtifactPath, e);
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
}
