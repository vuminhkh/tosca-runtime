package com.toscaruntime.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.google.common.collect.Maps;

public class DockerUtil {

    static {
        // TODO more elegant way to handle connection limit for the docker client ?
        System.setProperty("http.maxConnections", String.valueOf(Integer.MAX_VALUE));
    }

    public static DockerClient buildDockerClient(Map<String, String> providerProperties) {
        Properties properties = new Properties();
        properties.putAll(providerProperties);
        DockerClientConfig config = new DockerClientConfig.DockerClientConfigBuilder().withProperties(properties).build();
        return DockerClientBuilder.getInstance(config).build();
    }

    public static DockerClient buildDockerClient(String url, String certPath) {
        Map<String, String> providerProperties = Maps.newHashMap();
        providerProperties.put("docker.io.url", url);
        if (StringUtils.isNotBlank(certPath)) {
            providerProperties.put("docker.io.dockerCertPath", certPath);
        }
        return buildDockerClient(providerProperties);
    }

    public static String getImageTag(Image image) {
        String[] repoTags = image.getRepoTags();
        if (repoTags == null || repoTags.length == 0) {
            return "";
        } else {
            return repoTags[0];
        }
    }

    public static boolean imageExist(DockerClient dockerClient, String imageTag) {
        List<Image> images = dockerClient.listImagesCmd().withFilters("{\"dangling\":[\"true\"]}").exec();
        for (Image image : images) {
            if (DockerUtil.getImageTag(image).equals(imageTag)) {
                return true;
            }
        }
        return false;
    }

    public static void showLog(DockerClient dockerClient, String containerId, boolean follow, int numberOfLines, LogContainerResultCallback logCallback) {
        dockerClient.logContainerCmd(containerId).withStdOut(true).withStdErr(true).withFollowStream(follow).withTail(numberOfLines).exec(logCallback);
    }

    /**
     * Run command without blocking and return immediately the command's response as InputStream
     *
     * @param dockerClient the docker client
     * @param containerId  id of the container
     * @param commands     commands to be executed on the container
     * @return input stream of the response
     */
    public static InputStream runCommand(DockerClient dockerClient, String containerId, List<String> commands) {
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId).withAttachStdout(true).withAttachStderr(true)
                .withCmd(commands.toArray(new String[commands.size()]))
                .exec();
        return dockerClient.execStartCmd(containerId).withExecId(execCreateCmdResponse.getId()).exec();
    }

    public interface CommandLogger {
        void log(String line);
    }

    public static void runCommand(DockerClient dockerClient, String containerId, List<String> commands, Logger log) {
        runCommand(dockerClient, containerId, commands, new CommandLogger() {
            @Override
            public void log(String line) {
                log.info(line);
            }
        });
    }

    /**
     * Run command and block until the end of execution, log all output to the given logger
     *
     * @param dockerClient the docker client
     * @param containerId  id of the container
     * @param commands     commands to be executed on the container
     */
    public static void runCommand(DockerClient dockerClient, String containerId, List<String> commands, CommandLogger log) {
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId).withAttachStdout(true).withAttachStderr(true)
                .withCmd(commands.toArray(new String[commands.size()]))
                .exec();
        try (InputStream startResponse = dockerClient.execStartCmd(containerId).withExecId(execCreateCmdResponse.getId()).exec()) {
            BufferedReader scriptOutputReader = new BufferedReader(new InputStreamReader(new BufferedInputStream(startResponse), "UTF-8"));
            String line;
            while ((line = scriptOutputReader.readLine()) != null) {
                if (log != null) {
                    log.log(line);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Script " + commands + " exec encountered error while reading for output ", e);
        }
        InspectExecResponse response = dockerClient.inspectExecCmd(execCreateCmdResponse.getId()).exec();
        int exitStatus = response.getExitCode();
        if (exitStatus != 0) {
            throw new RuntimeException("Script " + commands + " exec has exited with error status " + exitStatus + " for container " + containerId);
        } else {
            if (log != null) {
                log.log("Script " + commands + "  exec has exited normally");
            }
        }
    }
}
