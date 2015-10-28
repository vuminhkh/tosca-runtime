package com.mkv.tosca.docker.nodes;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Link;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mkv.util.DockerUtil;

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
        if (containerId == null) {
            throw new RuntimeException("Node [" + getName() + "] : Container has not been created yet");
        }
        log.info("Node [" + getName() + "] : Starting container with id " + containerId);
        dockerClient.startContainerCmd(containerId).exec();
        ipAddress = dockerClient.inspectContainerCmd(containerId).exec().getNetworkSettings().getIpAddress();
        getAttributes().put("ip_address", ipAddress);
        log.info("Node [" + getName() + "] : Started container with id " + containerId + " and ip address " + ipAddress);
        runCommand(Lists.newArrayList("mkdir", "-p", RECIPE_LOCATION));
        dockerClient.copyFileToContainerCmd(containerId, this.config.getArtifactsPath().toString()).withDirChildrenOnly(true).withRemotePath(RECIPE_LOCATION).exec();
    }

    @Override
    public void stop() {
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
        if (containerId == null) {
            throw new RuntimeException("Container has not been created yet");
        }
        log.info("Node [" + getName() + "] : Deleting container with id " + containerId);
        dockerClient.removeContainerCmd(containerId).exec();
        log.info("Node [" + getName() + "] : Deleted container with id " + containerId);
        containerId = null;
    }

    @Override
    public void configure() {
        // Do nothing
    }

    public void runCommand(List<String> commands) {
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId).withAttachStdout().withAttachStderr()
                .withCmd(commands.toArray(new String[commands.size()]))
                .exec();
        InputStream startResponse = dockerClient.execStartCmd(containerId).withExecId(execCreateCmdResponse.getId()).exec();
        try {
            BufferedReader scriptOutputReader = new BufferedReader(new InputStreamReader(new BufferedInputStream(startResponse), "UTF-8"));
            String line;
            while ((line = scriptOutputReader.readLine()) != null) {
                log.info(line);
            }
        } catch (IOException e) {
            throw new RuntimeException("Script " + commands + " exec encountered error while reading for output ", e);
        } finally {
            IOUtils.closeQuietly(startResponse);
        }
        int exitStatus = dockerClient.inspectExecCmd(execCreateCmdResponse.getId()).exec().getExitCode();
        if (exitStatus != 0) {
            throw new RuntimeException("Script " + commands + " exec has exited with error status " + exitStatus);
        } else {
            log.info("Script " + commands + "  exec has exited normally");
        }
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
            runCommand(Lists.newArrayList("mkdir", "-p", containerGeneratedScriptDir));
            dockerClient.copyFileToContainerCmd(containerId, localGeneratedScriptPath.toString()).withRemotePath(containerGeneratedScriptDir).exec();
            String copiedScript = containerGeneratedScriptDir + "/" + localGeneratedScriptPath.getFileName().toString();
            runCommand(Lists.newArrayList("chmod", "+x", copiedScript));
            runCommand(Lists.newArrayList(copiedScript));
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
