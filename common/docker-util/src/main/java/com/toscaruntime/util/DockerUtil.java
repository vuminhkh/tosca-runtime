package com.toscaruntime.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
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
import com.toscaruntime.exception.OperationExecutionException;

public class DockerUtil {

    public static final String DEFAULT_DOCKER_URL = "unix:///var/run/docker.sock";

    public static final String DOCKER_URL_KEY = "docker.io.url";

    public static final String DOCKER_CERT_PATH_KEY = "docker.io.dockerCertPath";

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

    public static DockerClient buildDockerClient() {
        return DockerClientBuilder.getInstance().build();
    }

    public static String getDockerURL(Map<String, String> providerProperties) {
        return providerProperties.getOrDefault(DOCKER_URL_KEY, DEFAULT_DOCKER_URL);
    }

    public static String getDockerHostIP(Map<String, String> providerProperties) {
        String dockerURL = getDockerURL(providerProperties);
        return getDockerHostIP(dockerURL);
    }

    public static DockerClient buildDockerClient(String url, String certPath) {
        Map<String, String> providerProperties = Maps.newHashMap();
        providerProperties.put(DOCKER_URL_KEY, url);
        if (StringUtils.isNotBlank(certPath)) {
            providerProperties.put(DOCKER_CERT_PATH_KEY, certPath);
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
        runCommand(dockerClient, containerId, commands, line -> log.info("[" + operationName + "][" + line.getStreamType() + "]: " + line.getData()));
    }

    /**
     * Run command and block until the end of execution, log all output to the given logger
     *
     * @param dockerClient the docker client
     * @param containerId  id of the container
     * @param commands     commands to be executed on the container
     */
    public static void runCommand(DockerClient dockerClient, String containerId, List<String> commands, CommandLogger log) {
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                .withAttachStdin(false)
                .withTty(false)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withCmd(commands.toArray(new String[commands.size()]))
                .exec();
        try (InputStream startResponse = dockerClient.execStartCmd(containerId).withExecId(execCreateCmdResponse.getId()).exec()) {
            DockerStreamDecoder dockerStreamDecoder = new DockerStreamDecoder(startResponse);
            List<DockerStreamDecoder.DecoderResult> lines;
            while ((lines = dockerStreamDecoder.readLines()) != null) {
                lines.forEach(log::log);
            }
        } catch (IOException e) {
            throw new OperationExecutionException("Script " + commands + " exec encountered error while reading for output ", e);
        }
        InspectExecResponse response = dockerClient.inspectExecCmd(execCreateCmdResponse.getId()).exec();
        int exitStatus = response.getExitCode();
        if (exitStatus != 0) {
            throw new OperationExecutionException("Script " + commands + " exec has exited with error status " + exitStatus + " for container " + containerId);
        }
    }

    /**
     * Retrieve the docker host from the URL
     *
     * @param url url of docker
     * @return the ip address of the docker host
     */
    public static String getDockerHostIP(String url) {
        try {
            URL parsed = new URL(url);
            return InetAddress.getByName(parsed.getHost()).getHostAddress();
        } catch (MalformedURLException | UnknownHostException e) {
            return "127.0.0.1";
        }
    }
}
