package com.toscaruntime.util;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.core.command.PullImageResultCallback;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class DockerUtilTest {

    private static final Logger log = LoggerFactory.getLogger(DockerUtilTest.class);

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

    @Test
    @Ignore
    public void testExecutor() throws MalformedURLException, UnsupportedEncodingException, InterruptedException {
        DockerClient dockerClient = DockerUtil.buildDockerClient("http://129.185.67.86:2376", null);
        dockerClient.pullImageCmd("toscaruntime/ubuntu-trusty").exec(new PullImageResultCallback()).awaitCompletion();
        CreateContainerResponse container = dockerClient.createContainerCmd("toscaruntime/ubuntu-trusty").withCmd("sleep", "9999")
                .withName("test2").exec();
        try {
            dockerClient.startContainerCmd(container.getId()).exec();

            DockerExecutor dockerExecutor = new DockerExecutor(dockerClient, container.getId(), false);
            ExecutorService executorService = Executors.newCachedThreadPool();
            dockerExecutor.upload("/Users/vuminhkh/Projects/samples/tomcat-war/scripts/", "/tmp/");
            executorService.submit(() -> {
                Map<String, String> env = new HashMap<>();
                env.put("JAVA_HOME", "/opt/java");
                env.put("JAVA_URL", "http://download.oracle.com/otn-pub/java/jdk/7u75-b13/jdk-7u75-linux-x64.tar.gz");
                dockerExecutor.executeArtifact("install java", Paths.get("/Users/vuminhkh/Projects/samples/tomcat-war/scripts/java_install.sh"), "/tmp/java_install.sh", env);
            });
            executorService.submit(() -> {
                Map<String, String> env = new HashMap<>();
                env.put("TOMCAT_HOME", "/opt/tomcat");
                env.put("TOMCAT_PORT", "80");
                env.put("TOMCAT_URL", "http://mirrors.ircam.fr/pub/apache/tomcat/tomcat-8/v8.0.33/bin/apache-tomcat-8.0.33.tar.gz");
                dockerExecutor.executeArtifact("install tomcat", Paths.get("/Users/vuminhkh/Projects/samples/tomcat-war/scripts/tomcat_install.sh"), "/tmp/tomcat_install.sh", env);
            });
            executorService.shutdown();
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } finally {
            dockerClient.removeContainerCmd(container.getId()).withForce(true).exec();
        }
    }
}
