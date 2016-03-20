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

//    @Test
//    public void testExecutor() throws MalformedURLException, UnsupportedEncodingException, InterruptedException {
//        DockerDaemonConfig config = DockerUtil.getDefaultDockerDaemonConfig();
//        DockerClient dockerClient = DockerUtil.buildDockerClient(config.getUrl(), config.getCertPath());
//        CreateContainerResponse container = dockerClient.createContainerCmd("busybox").withCmd("sleep", "9999")
//                .withName("test").exec();
//        dockerClient.startContainerCmd(container.getId()).exec();
//        DockerExecutor dockerExecutor = new DockerExecutor(dockerClient, container.getId());
//        dockerExecutor.runCommand("cat", line -> {
//            System.out.println(line.getData());
//        }, new ByteArrayInputStream("TOTO".getBytes(StandardCharsets.UTF_8)));
//        DockerDaemonConfig config = DockerUtil.getDefaultDockerDaemonConfig();
//        DockerClient dockerClient = DockerUtil.buildDockerClient(config.getUrl(), config.getCertPath());
//
//        CreateContainerResponse container = dockerClient.createContainerCmd("busybox").withCmd("sleep", "9999")
//                .withName("test").exec();
//
//        dockerClient.startContainerCmd(container.getId()).exec();
//
//        InputStream stdin = new ByteArrayInputStream("STDIN\n".getBytes("UTF-8"));
//
//        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
//
//        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(container.getId())
//                .withAttachStdout(true).withAttachStdin(true).withCmd("cat").exec();
//        dockerClient.execStartCmd(execCreateCmdResponse.getId()).withDetach(false).withTty(true).withStdIn(stdin)
//                .exec(new ExecStartResultCallback(stdout, System.err)).awaitCompletion(5, TimeUnit.SECONDS);
//
//        Assert.assertEquals(stdout.toString("UTF-8"), "STDIN\n");
//    }
}
