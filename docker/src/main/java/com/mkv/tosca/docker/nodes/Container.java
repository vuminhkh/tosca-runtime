package com.mkv.tosca.docker.nodes;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tosca.nodes.Compute;
import tosca.nodes.Root;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Link;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Volume;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class Container extends Compute {

    private static final Logger log = LoggerFactory.getLogger(Container.class);

    public static final String GENERATED_SCRIPT_PATH = "/.generated";

    private DockerClient dockerClient;

    private String containerId;

    private String ipAddress;

    private static final String RECIPE_LOCATION = "/var/recipe";

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

    @Override
    public void create() {
        String imageId = getImageId();
        log.info("Node [" + getName() + "] : Creating container with image " + imageId);
        Volume recipeVolume = new Volume(RECIPE_LOCATION);
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
        containerId = dockerClient.createContainerCmd(imageId).withName(getId()).withLinks(links.toArray(new Link[links.size()]))
                .withBinds(new Bind(recipeLocalPath, recipeVolume)).withExposedPorts(exposedPorts.toArray(new ExposedPort[exposedPorts.size()]))
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
    }

    @Override
    public void stop() {
        if (containerId == null) {
            throw new RuntimeException("Container has not been created yet");
        }
        log.info("Node [" + getName() + "] : Stopping container with id " + containerId);
        dockerClient.stopContainerCmd(containerId).exec();
        log.info("Node [" + getName() + "] : Started container with id " + containerId + " and ip address " + ipAddress);
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
        containerId = null;
        log.info("Node [" + getName() + "] : Deleted container with id " + containerId);
    }

    public void runCommand(List<String> commands) throws IOException {
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
        String containerGeneratedScriptPath = RECIPE_GENERATED_SCRIPT_LOCATION + "/" + getId() + "/" + operationArtifactPath;
        String containerScriptPath = RECIPE_LOCATION + "/" + operationArtifactPath;
        String localGeneratedScriptPath = recipeLocalPath + GENERATED_SCRIPT_PATH + "/" + getId() + "/" + operationArtifactPath;
        PrintWriter localGeneratedScriptWriter = null;
        try {
            Files.createDirectories(Paths.get(localGeneratedScriptPath).getParent());
            localGeneratedScriptWriter = new PrintWriter(new BufferedOutputStream(new FileOutputStream(localGeneratedScriptPath)));
            localGeneratedScriptWriter.write("#!/bin/sh -e\n");
            if (environmentVariables != null) {
                for (Map.Entry<String, String> envEntry : environmentVariables.entrySet()) {
                    localGeneratedScriptWriter.write("export " + envEntry.getKey() + "=" + envEntry.getValue() + "\n");
                }
            }
            localGeneratedScriptWriter.write("chmod +x " + containerScriptPath + "\n");
            localGeneratedScriptWriter.write(containerScriptPath + "\n");
            localGeneratedScriptWriter.flush();
            runCommand(Lists.newArrayList("chmod", "+x", containerGeneratedScriptPath));
            runCommand(Lists.newArrayList(containerGeneratedScriptPath));
        } catch (IOException e) {
            throw new RuntimeException("Unable to create generated script at " + localGeneratedScriptPath, e);
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
