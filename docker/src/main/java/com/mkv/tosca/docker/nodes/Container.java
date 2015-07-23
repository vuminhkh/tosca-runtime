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
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tosca.nodes.Compute;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;
import com.google.common.collect.Lists;

public class Container extends Compute {

    private static final Logger log = LoggerFactory.getLogger(Container.class);

    public static final String GENERATED_SCRIPT_PATH = "/.generated";

    private DockerClient dockerClient;

    private String imageId;

    private String containerId;

    private String ipAddress;

    private String recipeLocalPath;

    private static final String RECIPE_LOCATION = "/var/recipe";

    private static final String RECIPE_GENERATED_SCRIPT_LOCATION = RECIPE_LOCATION + GENERATED_SCRIPT_PATH;

    @Override
    public void create() {
        log.info("Node [" + getName() + "] : Creating container with image " + imageId);
        Volume recipeVolume = new Volume(RECIPE_LOCATION);
        containerId = dockerClient.createContainerCmd(imageId).withName(getName()).withBinds(new Bind(recipeLocalPath, recipeVolume)).exec().getId();
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
        log.info("Node [" + getName() + "] : Started container with id " + containerId + " and ip address " + ipAddress);
    }

    @Override
    public void stop() {
        if (containerId == null) {
            throw new RuntimeException("Container has not been created yet");
        }
        log.info("Node [" + getName() + "] : Stopping container with id " + containerId);
        dockerClient.stopContainerCmd(containerId).exec();
        ipAddress = null;
        log.info("Node [" + getName() + "] : Started container with id " + containerId + " and ip address " + ipAddress);
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

    private void runCommand(List<String> commands) throws IOException {
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId).withAttachStdout().withAttachStderr()
                .withCmd(commands.toArray(new String[commands.size()]))
                .exec();
        InputStream startResponse = dockerClient.execStartCmd(containerId).withExecId(execCreateCmdResponse.getId()).exec();
        BufferedReader scriptOutputReader = new BufferedReader(new InputStreamReader(new BufferedInputStream(startResponse), "UTF-8"));
        String line;
        while ((line = scriptOutputReader.readLine()) != null) {
            log.info(line);
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
        String containerGeneratedScriptPath = RECIPE_GENERATED_SCRIPT_LOCATION + "/" + operationArtifactPath;
        String containerScriptPath = RECIPE_LOCATION + "/" + operationArtifactPath;
        String localGeneratedScriptPath = recipeLocalPath + GENERATED_SCRIPT_PATH + "/" + operationArtifactPath;
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

    public void setImageId(String imageId) {
        this.imageId = imageId;
    }

    public void setDockerClient(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getRecipeLocalPath() {
        return recipeLocalPath;
    }

    public void setRecipeLocalPath(String recipeLocalPath) {
        this.recipeLocalPath = recipeLocalPath;
    }

    public DockerClient getDockerClient() {
        return dockerClient;
    }
}
