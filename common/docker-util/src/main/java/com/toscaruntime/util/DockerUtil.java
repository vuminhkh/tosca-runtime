package com.toscaruntime.util;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Paths;
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
import com.github.dockerjava.jaxrs.DockerCmdExecFactoryImpl;
import com.google.common.collect.Maps;
import com.toscaruntime.exception.OperationExecutionException;

public class DockerUtil {

    public static final String DEFAULT_DOCKER_URL_FOR_LINUX = "unix:///var/run/docker.sock";

    public static final String DEFAULT_DOCKER_URL_FOR_MAC_WINDOWS = "https://192.168.99.100:2376";

    public static final String DOCKER_URL_KEY = "docker.io.url";

    public static final String DOCKER_CERT_PATH_KEY = "docker.io.dockerCertPath";

    public static DockerClient buildDockerClient(Map<String, String> providerProperties) {
        Properties properties = new Properties();
        properties.putAll(providerProperties);
        DockerClientConfig config = new DockerClientConfig.DockerClientConfigBuilder().withProperties(properties).build();
        DockerCmdExecFactoryImpl execFactory = new DockerCmdExecFactoryImpl();
        execFactory.withMaxTotalConnections(Integer.MAX_VALUE);
        execFactory.withMaxPerRouteConnections(Integer.MAX_VALUE);
        return DockerClientBuilder.getInstance(config).withDockerCmdExecFactory(execFactory).build();
    }

    public static DockerClient buildDockerClient() {
        return DockerClientBuilder.getInstance().build();
    }

    public static String getDockerURL(Map<String, String> providerProperties) {
        return providerProperties.getOrDefault(DOCKER_URL_KEY, DEFAULT_DOCKER_URL_FOR_LINUX);
    }

    public static DockerDaemonConfig getDefaultDockerDaemonConfig() throws MalformedURLException {
        String dockerHostFromEnv = System.getenv("DOCKER_HOST");
        String dockerURLFromProperty = System.getProperty(DOCKER_URL_KEY);
        if (StringUtils.isNotBlank(dockerHostFromEnv)) {
            String useTLSFromEnv = System.getenv("DOCKER_TLS_VERIFY");
            String protocol = "1".equals(useTLSFromEnv) ? "https" : "http";
            URL parsed = new URL(dockerHostFromEnv.replace("tcp", protocol));
            String host = parsed.getHost();
            int port = parsed.getPort();
            return new DockerDaemonConfig(protocol + "://" + host + ":" + port, System.getenv("DOCKER_CERT_PATH"));
        } else if (StringUtils.isNotBlank(dockerURLFromProperty)) {
            return new DockerDaemonConfig(dockerURLFromProperty, System.getProperty(DOCKER_CERT_PATH_KEY));
        } else {
            String osName = System.getProperty("os.name");
            if (osName.startsWith("Windows") || osName.startsWith("Mac")) {
                String url = DEFAULT_DOCKER_URL_FOR_MAC_WINDOWS;
                String certPath = Paths.get(System.getProperty("user.home")).resolve(".docker").resolve("machine").resolve("machines").resolve("default").toString();
                return new DockerDaemonConfig(url, certPath);
            } else {
                return new DockerDaemonConfig(DEFAULT_DOCKER_URL_FOR_LINUX, null);
            }
        }
    }

    public static String getDockerHostIP(Map<String, String> providerProperties) throws UnknownHostException {
        String dockerURL = getDockerURL(providerProperties);
        return InetAddress.getByName(getDockerHost(dockerURL)).getHostAddress();
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
        try {
            DockerStreamDecoder dockerStreamDecoder = new DockerStreamDecoder(log);
            dockerClient.execStartCmd(containerId).withExecId(execCreateCmdResponse.getId()).exec(dockerStreamDecoder).awaitCompletion();
        } catch (Exception e) {
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
    public static String getDockerHost(String url) {
        try {
            URL parsed = new URL(url);
            return parsed.getHost();
        } catch (MalformedURLException e) {
            return "127.0.0.1";
        }
    }

    public static String normalizeResourceName(String name) {
        return name.replaceAll("[^\\p{L}\\p{Nd}]+", "_");
    }
}
