package com.mkv.util;

import java.util.Map;
import java.util.Properties;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;

public class DockerUtil {

    public static DockerClient buildDockerClient(Map<String, String> providerProperties) {
        System.setProperty("http.maxConnections", String.valueOf(Integer.MAX_VALUE));
        Properties properties = new Properties();
        properties.putAll(providerProperties);
        DockerClientConfig config = new DockerClientConfig.DockerClientConfigBuilder().withProperties(properties).build();
        DockerClient dockerClient = DockerClientBuilder.getInstance(config).build();
        return dockerClient;
    }
}
