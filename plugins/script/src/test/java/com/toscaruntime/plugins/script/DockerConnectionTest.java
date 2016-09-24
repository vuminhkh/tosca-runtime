package com.toscaruntime.plugins.script;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.google.common.collect.ImmutableMap;
import com.toscaruntime.artifact.SimpleOutputHandler;
import com.toscaruntime.util.ClassLoaderUtil;
import com.toscaruntime.util.DockerConnection;
import com.toscaruntime.util.DockerDaemonConfig;
import com.toscaruntime.util.DockerUtil;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.charset.StandardCharsets;

@RunWith(JUnit4.class)
public class DockerConnectionTest {

    @Test
    public void testConnection() throws Exception {
        DockerDaemonConfig defaultConfig = DockerUtil.getDefaultDockerDaemonConfig();
        DockerClient dockerClient = DockerUtil.buildDockerClient(defaultConfig.getUrl(), defaultConfig.getCertPath());
        dockerClient.pullImageCmd("toscaruntime/ubuntu-trusty").exec(new PullImageResultCallback()).awaitCompletion();
        CreateContainerResponse container = dockerClient.createContainerCmd("toscaruntime/ubuntu-trusty").withCmd("sleep", "9999")
                .withName("DockerConnectionTest").exec();
        try {
            dockerClient.startContainerCmd(container.getId()).exec();
            DockerConnection dockerConnection = new DockerConnection();
            dockerConnection.initialize(ImmutableMap.<String, Object>builder()
                    .put("docker_url", defaultConfig.getUrl())
                    .put("cert_path", defaultConfig.getCertPath())
                    .put("container_id", container.getId()).build());
            Assert.assertEquals(0, (int) dockerConnection.executeScript(new String(IOUtils.toByteArray(this.getClass().getClassLoader()
                    .getResourceAsStream("scripts/good-script.sh")), StandardCharsets.UTF_8), ImmutableMap.<String, String>builder().put("MY_VARIABLE", "Good variable").build(), new SimpleOutputHandler()));
            Assert.assertNotEquals(0, (int) dockerConnection.executeScript(new String(IOUtils.toByteArray(this.getClass().getClassLoader()
                    .getResourceAsStream("scripts/bad-script.sh"))), ImmutableMap.<String, String>builder().put("MY_VARIABLE", "Bad variable").build(), new SimpleOutputHandler()));
            Assert.assertEquals(0, (int) dockerConnection.executeCommand("echo \"I'm  a good command\""));
            Assert.assertNotEquals(0, (int) dockerConnection.executeCommand("cd /tmp/scripts"));
            dockerConnection.upload(ClassLoaderUtil.getPathForResource("scripts").toString(), "/tmp/scripts");
            Assert.assertEquals(0, (int) dockerConnection.executeCommand("cd /tmp/scripts"));
        } finally {
            dockerClient.removeContainerCmd(container.getId()).withForce(true).exec();
        }
    }
}
