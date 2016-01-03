package com.toscaruntime.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
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

    public static void showLog(DockerClient dockerClient, String containerId, boolean follow, int numberOfLines, LogContainerResultCallback logCallback) {
        dockerClient.logContainerCmd(containerId).withStdOut(true).withStdErr(true).withFollowStream(follow).withTail(numberOfLines).exec(logCallback);
    }

    public interface CommandLogger {
        void log(DockerStreamDecoder.DecoderResult line);
    }

    public static void runCommand(DockerClient dockerClient, String containerId, String operationName, List<String> commands, Logger log) {
        runCommand(dockerClient, containerId, commands, line -> {
            switch (line.getStreamType()) {
                case STD_IN:
                    log.info("[" + operationName + "][stdin]: " + line.getData());
                    break;
                case STD_OUT:
                    log.info("[" + operationName + "][stdout]: " + line.getData());
                    break;
                case STD_ERR:
                    log.info("[" + operationName + "][stderr]: " + line.getData());
                    break;
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
            DockerStreamDecoder dockerStreamDecoder = new DockerStreamDecoder(startResponse);
            List<DockerStreamDecoder.DecoderResult> lines;
            while ((lines = dockerStreamDecoder.readLines()) != null) {
                lines.forEach(log::log);
            }
        } catch (IOException e) {
            throw new RuntimeException("Script " + commands + " exec encountered error while reading for output ", e);
        }
        InspectExecResponse response = dockerClient.inspectExecCmd(execCreateCmdResponse.getId()).exec();
        int exitStatus = response.getExitCode();
        if (exitStatus != 0) {
            throw new RuntimeException("Script " + commands + " exec has exited with error status " + exitStatus + " for container " + containerId);
        }
    }
}
