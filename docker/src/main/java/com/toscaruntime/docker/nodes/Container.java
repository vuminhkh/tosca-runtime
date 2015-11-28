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

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Link;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.toscaruntime.util.DockerUtil;

import tosca.nodes.Compute;
import tosca.nodes.Root;

public class Container extends Compute {

    private static final Logger log = LoggerFactory.getLogger(Container.class);

    private DockerClient dockerClient;

    private String containerId;

    private String ipAddress;

    private static final String RECIPE_LOCATION = "/var/recipe";

    public static final String GENERATED_SCRIPT_PATH = "/.generated";

    private static final String RECIPE_GENERATED_SCRIPT_LOCATION = RECIPE_LOCATION + GENERATED_SCRIPT_PATH;

    public String getImageId() {
        return getProperty("image_id");
    }

    public String[] getExposedPorts() {
        String exposedPortsRaw = getProperty("exposed_ports");
        if (exposedPortsRaw != null) {
            List<String> exposedPortsList = Lists.newArrayList();
            for (String exposedPortRaw : exposedPortsRaw.split(",")) {
                exposedPortsList.add(exposedPortRaw.trim());
            }
            return exposedPortsList.toArray(new String[exposedPortsList.size()]);
        } else {
            return new String[0];
        }
    }

    public Map<String, String> getPortsMapping() {
        Map<String, String> mapping = new HashMap<>();
        String portMappingsRaw = getProperty("port_mappings");
        if (portMappingsRaw == null) {
            return mapping;
        }
        String[] portMappingsEntriesRaw = portMappingsRaw.split(",");
        for (String portMappingEntryRaw : portMappingsEntriesRaw) {
            String[] entry = portMappingEntryRaw.split("-");
            if (entry.length == 2) {
                mapping.put(entry[0].trim(), entry[1].trim());
            }
        }
        return mapping;
    }

    private static synchronized void pullImageIfNotExisting(DockerClient dockerClient, String imageTag) {
        if (!DockerUtil.imageExist(dockerClient, imageTag)) {
            dockerClient.pullImageCmd(imageTag).exec(new PullImageResultCallback()).awaitSuccess();
        }
    }

    @Override
    public void create() {
        super.create();
        String imageId = getImageId();
        log.info("Node [" + getName() + "] : Creating container with image " + imageId);
        Set<String> linkedWithContainers = Sets.newHashSet();
        for (Root child : getChildren()) {
            for (Root childDependency : child.getDependsOnNodes()) {
                Compute childDependencyHost = childDependency.getHost();
                if (childDependencyHost != null && childDependencyHost instanceof Container) {
                    linkedWithContainers.add(childDependencyHost.getId());
                }
            }
        }
        List<Link> links = Lists.newArrayList();
        for (String linkedContainer : linkedWithContainers) {
            links.add(new Link(linkedContainer, linkedContainer));
        }
        String[] exposedPortsRaw = getExposedPorts();
        Map<String, String> portMappingsRaw = getPortsMapping();
        List<ExposedPort> exposedPorts = Lists.newArrayList();
        Ports portBindings = new Ports();
        if (exposedPortsRaw.length > 0) {
            for (String exposedPort : exposedPortsRaw) {
                try {
                    int port = Integer.parseInt(exposedPort);
                    ExposedPort portTcp = ExposedPort.tcp(port);
                    ExposedPort portUdp = ExposedPort.udp(port);
                    exposedPorts.add(portTcp);
                    exposedPorts.add(portUdp);
                    String mappedPortRaw = portMappingsRaw.get(exposedPort);
                    if (mappedPortRaw != null) {
                        int mappedPort = Integer.parseInt(mappedPortRaw);
                        portBindings.bind(portTcp, Ports.Binding(mappedPort));
                        portBindings.bind(portUdp, Ports.Binding(mappedPort));
                    }
                } catch (Exception e) {
                    log.error("Port exposed not in good format " + exposedPort, e);
                }
            }
        }
        String imageTag = imageId;
        if (!imageTag.contains(":")) {
            imageTag += ":latest";
        }
        if (!DockerUtil.imageExist(dockerClient, imageTag)) {
            pullImageIfNotExisting(dockerClient, imageTag);
        }
        containerId = dockerClient.createContainerCmd(imageId)
                .withName(getId())
                .withLinks(links.toArray(new Link[links.size()]))
                .withExposedPorts(exposedPorts.toArray(new ExposedPort[exposedPorts.size()]))
                .withPortBindings(portBindings).exec().getId();
        log.info("Node [" + getName() + "] : Created container with id " + containerId);
    }

    @Override
    public void start() {
        super.start();
        if (containerId == null) {
            throw new RuntimeException("Node [" + getName() + "] : Container has not been created yet");
        }
        log.info("Node [" + getName() + "] : Starting container with id " + containerId);
        dockerClient.startContainerCmd(containerId).exec();
        InspectContainerResponse response = dockerClient.inspectContainerCmd(containerId).exec();
        ipAddress = response.getNetworkSettings().getIpAddress();
        getAttributes().put("ip_address", ipAddress);
        getAttributes().put("tosca_id", containerId);
        getAttributes().put("tosca_name", response.getName());
        log.info("Node [" + getName() + "] : Started container with id " + containerId + " and ip address " + ipAddress);
        DockerUtil.runCommand(dockerClient, containerId, Lists.newArrayList("mkdir", "-p", RECIPE_LOCATION), log);
        dockerClient.copyFileToContainerCmd(containerId, this.config.getArtifactsPath().toString()).withDirChildrenOnly(true).withRemotePath(RECIPE_LOCATION).exec();
    }

    @Override
    public void stop() {
        super.stop();
        if (containerId == null) {
            throw new RuntimeException("Container has not been created yet");
        }
        log.info("Node [" + getName() + "] : Stopping container with id " + containerId);
        dockerClient.stopContainerCmd(containerId).exec();
        log.info("Node [" + getName() + "] : Stopped container with id " + containerId + " and ip address " + ipAddress);
        getAttributes().remove("ip_address");
        ipAddress = null;
    }

    @Override
    public void delete() {
        super.delete();
        if (containerId == null) {
            throw new RuntimeException("Container has not been created yet");
        }
        log.info("Node [" + getName() + "] : Deleting container with id " + containerId);
        dockerClient.removeContainerCmd(containerId).exec();
        log.info("Node [" + getName() + "] : Deleted container with id " + containerId);
        containerId = null;
    }

    /**
     * Use docker exec to run scripts inside docker container
     *
     * @param operationArtifactPath the relative path to the script in the recipe
     */
    public void execute(String operationArtifactPath, Map<String, String> environmentVariables) {
        String containerGeneratedScriptDir = Paths.get(RECIPE_GENERATED_SCRIPT_LOCATION + "/" + getId() + "/" + operationArtifactPath).getParent().toString();
        String containerScriptPath = RECIPE_LOCATION + "/" + operationArtifactPath;
        PrintWriter localGeneratedScriptWriter = null;
        try {
            Path localGeneratedScriptPath = Files.createTempFile("", ".sh");
            Files.createDirectories(localGeneratedScriptPath.getParent());
            localGeneratedScriptWriter = new PrintWriter(new BufferedOutputStream(Files.newOutputStream(localGeneratedScriptPath)));
            localGeneratedScriptWriter.write("#!/bin/sh -e\n");
            if (environmentVariables != null) {
                for (Map.Entry<String, String> envEntry : environmentVariables.entrySet()) {
                    localGeneratedScriptWriter.write("export " + envEntry.getKey() + "=" + envEntry.getValue() + "\n");
                }
            }
            localGeneratedScriptWriter.write("chmod +x " + containerScriptPath + "\n");
            localGeneratedScriptWriter.write(containerScriptPath + "\n");
            localGeneratedScriptWriter.flush();
            DockerUtil.runCommand(dockerClient, containerId, Lists.newArrayList("mkdir", "-p", containerGeneratedScriptDir), log);
            dockerClient.copyFileToContainerCmd(containerId, localGeneratedScriptPath.toString()).withRemotePath(containerGeneratedScriptDir).exec();
            String copiedScript = containerGeneratedScriptDir + "/" + localGeneratedScriptPath.getFileName().toString();
            DockerUtil.runCommand(dockerClient, containerId, Lists.newArrayList("chmod", "+x", copiedScript), log);
            DockerUtil.runCommand(dockerClient, containerId, Lists.newArrayList(copiedScript), log);
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
}
