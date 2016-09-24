package com.toscaruntime.plugins.script;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.google.common.collect.ImmutableMap;
import com.toscaruntime.artifact.Executor;
import com.toscaruntime.exception.deployment.artifact.ArtifactExecutionException;
import com.toscaruntime.plugins.script.bash.BashExecutor;
import com.toscaruntime.util.ClassLoaderUtil;
import com.toscaruntime.util.DockerConnection;
import com.toscaruntime.util.DockerDaemonConfig;
import com.toscaruntime.util.DockerUtil;
import com.toscaruntime.util.SSHConnection;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collections;
import java.util.Map;

@RunWith(JUnit4.class)
public class BashExecutorTest {

    @Test
    public void testDocker() throws Exception {
        DockerDaemonConfig defaultConfig = DockerUtil.getDefaultDockerDaemonConfig();
        DockerClient dockerClient = DockerUtil.buildDockerClient(defaultConfig.getUrl(), defaultConfig.getCertPath());
        dockerClient.pullImageCmd("toscaruntime/ubuntu-trusty").exec(new PullImageResultCallback()).awaitCompletion();
        CreateContainerResponse container = dockerClient.createContainerCmd("toscaruntime/ubuntu-trusty").withCmd("sleep", "9999")
                .withName("DockerBashTest").exec();
        try {
            dockerClient.startContainerCmd(container.getId()).exec();
            DockerConnection dockerConnection = new DockerConnection();
            dockerConnection.initialize(ImmutableMap.<String, Object>builder()
                    .put("docker_url", defaultConfig.getUrl())
                    .put("cert_path", defaultConfig.getCertPath())
                    .put("container_id", container.getId()).build());
            BashExecutor bashExecutor = new BashExecutor();
            bashExecutor.initialize(dockerConnection, ImmutableMap.<String, Object>builder()
                    .put(Executor.LOCAL_RECIPE_LOCATION_KEY, ClassLoaderUtil.getPathForResource("scripts").toString())
                    .put(Executor.RECIPE_LOCATION_KEY, "/tmp/recipe")
                    .build()
            );
            bashExecutor.executeArtifact("testNode", "testOperation", "good-script.sh", ImmutableMap.<String, Object>builder().put("MY_VARIABLE", "Good variable").build(), Collections.emptyMap());
            try {
                bashExecutor.executeArtifact("testNode", "testOperation", "bad-script.sh", ImmutableMap.<String, Object>builder().put("MY_VARIABLE", "Bad variable").build(), Collections.emptyMap());
                Assert.fail("This script must fail");
            } catch (ArtifactExecutionException ignored) {
            }
            Map<String, String> outputs = bashExecutor.executeArtifact("testNode", "testOperation", "output-script.sh", ImmutableMap.<String, Object>builder().put("MY_VARIABLE", "Good variable").build(), Collections.emptyMap());
            Assert.assertNotNull(outputs);
            Assert.assertEquals("Good variable", outputs.get("MY_OUTPUT"));
        } finally {
            dockerClient.removeContainerCmd(container.getId()).withForce(true).exec();
        }
    }

    @Test
    public void testSSH() throws Exception {
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
                .withName("SSHBashTest").exec();
        try {
            dockerClient.startContainerCmd(container.getId()).exec();
            InspectContainerResponse containerDetails = dockerClient.inspectContainerCmd(container.getId()).exec();
            Thread.sleep(2000);
            SSHConnection sshConnection = new SSHConnection();
            sshConnection.initialize(ImmutableMap.<String, Object>builder()
                    .put("user", "root")
                    .put("ip", DockerUtil.getDockerHost(defaultConfig.getUrl()))
                    .put("port", containerDetails.getNetworkSettings().getPorts().getBindings().get(sshPort)[0].getHostPortSpec())
                    .put("pem_path", ClassLoaderUtil.getPathForResource("insecure_key.pem").toString())
                    .build());
            BashExecutor bashExecutor = new BashExecutor();
            bashExecutor.initialize(sshConnection, ImmutableMap.<String, Object>builder()
                    .put(Executor.LOCAL_RECIPE_LOCATION_KEY, ClassLoaderUtil.getPathForResource("scripts").toString())
                    .put(Executor.RECIPE_LOCATION_KEY, "/tmp/recipe")
                    .build()
            );
            bashExecutor.executeArtifact("testNode", "testOperation", "good-script.sh", ImmutableMap.<String, Object>builder().put("MY_VARIABLE", "Good variable").build(), Collections.emptyMap());
            try {
                bashExecutor.executeArtifact("testNode", "testOperation", "bad-script.sh", ImmutableMap.<String, Object>builder().put("MY_VARIABLE", "Bad variable").build(), Collections.emptyMap());
                Assert.fail("This script must fail");
            } catch (ArtifactExecutionException ignored) {
            }
            Map<String, String> outputs = bashExecutor.executeArtifact("testNode", "testOperation", "output-script.sh", ImmutableMap.<String, Object>builder().put("MY_VARIABLE", "Good variable").build(), Collections.emptyMap());
            Assert.assertNotNull(outputs);
            Assert.assertEquals("Good variable", outputs.get("MY_OUTPUT"));
        } finally {
            dockerClient.removeContainerCmd(container.getId()).withForce(true).exec();
        }
    }
}
