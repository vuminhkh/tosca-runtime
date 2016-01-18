package com.toscaruntime.util;

import java.net.MalformedURLException;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DockerUtilTest {

    @Test
    public void testGetDefaultURL() throws MalformedURLException {
        String url = "https://192.168.99.100:2376";
        String certPath = Paths.get(System.getProperty("user.home")).resolve(".docker").resolve("machine").resolve("machines").resolve("default").toString();
        System.setProperty(DockerUtil.DOCKER_URL_KEY, url);
        System.setProperty(DockerUtil.DOCKER_CERT_PATH_KEY, certPath);
        DockerDaemonConfig config = DockerUtil.getDefaultDockerDaemonConfig();
        Assert.assertEquals(url, config.getUrl());
        Assert.assertEquals(certPath, config.getCertPath());
    }

    @Test
    public void testResolveDockerHost() throws MalformedURLException {
        String url = "https://192.168.99.100:2376";
        String dockerHost = DockerUtil.getDockerHost(url);
        Assert.assertEquals("192.168.99.100", dockerHost);
    }
}
