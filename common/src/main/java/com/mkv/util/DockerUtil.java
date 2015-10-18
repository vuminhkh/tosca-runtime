package com.mkv.util;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.google.common.collect.Maps;

public class DockerUtil {

    public static DockerClient buildDockerClient(Map<String, String> providerProperties) {
        System.setProperty("http.maxConnections", String.valueOf(Integer.MAX_VALUE));
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
}
