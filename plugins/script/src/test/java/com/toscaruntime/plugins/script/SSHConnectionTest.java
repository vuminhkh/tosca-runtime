package com.toscaruntime.plugins.script;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.google.common.collect.ImmutableMap;
import com.toscaruntime.artifact.SimpleOutputHandler;
import com.toscaruntime.util.ClassLoaderUtil;
import com.toscaruntime.util.DockerDaemonConfig;
import com.toscaruntime.util.DockerUtil;
import com.toscaruntime.util.SSHConnection;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.charset.StandardCharsets;

@RunWith(JUnit4.class)
public class SSHConnectionTest {

    @Test
    public void testConnection() throws Exception {
        DockerDaemonConfig defaultConfig = DockerUtil.getDefaultDockerDaemonConfig();
        DockerClient dockerClient = DockerUtil.buildDockerClient(defaultConfig.getUrl(), defaultConfig.getCertPath());
        dockerClient.pullImageCmd("toscaruntime/ssh-enabled").exec(new PullImageResultCallback()).awaitCompletion();
        Ports portBindings = new Ports();
        ExposedPort sshPort = ExposedPort.tcp(22);
        portBindings.bind(sshPort, Ports.Binding.empty());
        CreateContainerResponse container = dockerClient.createContainerCmd("toscaruntime/ssh-enabled")
                .withCmd("/sbin/my_init", "--enable-insecure-key")
                .withExposedPorts(sshPort)
                .withPortBindings(portBindings)
                .withName("SSHConnectionTest").exec();
        try {
            dockerClient.startContainerCmd(container.getId()).exec();
            InspectContainerResponse containerDetails = dockerClient.inspectContainerCmd(container.getId()).exec();
            Thread.sleep(2000);
            SSHConnection sshConnection = new SSHConnection();
            sshConnection.initialize(ImmutableMap.<String, Object>builder()
                    .put("user", "root")
                    .put("ip", DockerUtil.getDockerHost(defaultConfig.getUrl()))
                    .put("port", containerDetails.getNetworkSettings().getPorts().getBindings().get(sshPort)[0].getHostPortSpec())
                    .put("pem_path", ClassLoaderUtil.getPathForResource("insecure_key").toString())
                    .build());
            Assert.assertEquals(0, (int) sshConnection.executeScript(new String(IOUtils.toByteArray(this.getClass().getClassLoader()
                    .getResourceAsStream("scripts/good-script.sh")), StandardCharsets.UTF_8), ImmutableMap.<String, String>builder().put("MY_VARIABLE", "Good variable").build(), new SimpleOutputHandler()));
            Assert.assertNotEquals(0, (int) sshConnection.executeScript(new String(IOUtils.toByteArray(this.getClass().getClassLoader()
                    .getResourceAsStream("scripts/bad-script.sh"))), ImmutableMap.<String, String>builder().put("MY_VARIABLE", "Bad variable").build(), new SimpleOutputHandler()));
            Assert.assertEquals(0, (int) sshConnection.executeCommand("echo \"I'm  a good command\""));
            Assert.assertNotEquals(0, (int) sshConnection.executeCommand("cd /tmp/scripts"));
            sshConnection.upload(ClassLoaderUtil.getPathForResource("scripts").toAbsolutePath().toString(), "/tmp/scripts");
            Assert.assertEquals(0, (int) sshConnection.executeCommand("cd /tmp/scripts"));
        } finally {
            dockerClient.removeContainerCmd(container.getId()).withForce(true).exec();
        }
    }
}