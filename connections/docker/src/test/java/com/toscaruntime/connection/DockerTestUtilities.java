package com.toscaruntime.connection;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.google.common.collect.ImmutableMap;
import com.toscaruntime.artifact.Connection;
import com.toscaruntime.util.ClassLoaderUtil;
import com.toscaruntime.util.DockerDaemonConfig;
import com.toscaruntime.util.DockerUtil;

import java.util.Map;

public class DockerTestUtilities {

    public interface TestWithDocker {
        void test(Map<String, Object> containerProperties) throws Exception;
    }

    public interface TestWithAnsibleControlMachine {
        void test(Connection controlMachineConnection) throws Exception;
    }

    public static void testWithAnsibleControlMachine(TestWithAnsibleControlMachine test) throws Exception {
        DockerDaemonConfig defaultConfig = DockerUtil.getDefaultDockerDaemonConfig();
        DockerClient dockerClient = DockerUtil.buildDockerClient(defaultConfig);
        dockerClient.pullImageCmd("toscaruntime/ansible").exec(new PullImageResultCallback()).awaitCompletion();
        CreateContainerResponse container = dockerClient.createContainerCmd("toscaruntime/ansible")
                .withCmd("/sbin/my_init")
                .exec();
        try {
            dockerClient.startContainerCmd(container.getId()).exec();
            DockerConnection ansibleControlMachineConnection = new DockerConnection();
            ansibleControlMachineConnection.initialize(ImmutableMap.<String, Object>builder()
                    .put(Connection.TARGET, container.getId())
                    .put(DockerClientConfig.DOCKER_HOST, defaultConfig.getHost())
                    .put(DockerClientConfig.DOCKER_CERT_PATH, defaultConfig.getCertPath())
                    .put(DockerClientConfig.DOCKER_TLS_VERIFY, defaultConfig.getTlsVerify())
                    .build());
            test.test(ansibleControlMachineConnection);
        } finally {
            dockerClient.removeContainerCmd(container.getId()).withForce(true).exec();
        }
    }

    public static void testWithSSHEnabledContainer(TestWithDocker test) throws Exception {
        DockerDaemonConfig defaultConfig = DockerUtil.getDefaultDockerDaemonConfig();
        DockerClient dockerClient = DockerUtil.buildDockerClient(defaultConfig);
        dockerClient.pullImageCmd("toscaruntime/ssh-enabled").exec(new PullImageResultCallback()).awaitCompletion();
        Ports portBindings = new Ports();
        ExposedPort sshPort = ExposedPort.tcp(22);
        portBindings.bind(sshPort, Ports.Binding.empty());
        CreateContainerResponse container = dockerClient.createContainerCmd("toscaruntime/ssh-enabled")
                .withCmd("/sbin/my_init", "--enable-insecure-key")
                .withExposedPorts(sshPort)
                .withPortBindings(portBindings)
                .exec();
        try {
            dockerClient.startContainerCmd(container.getId()).exec();
            InspectContainerResponse containerDetails = dockerClient.inspectContainerCmd(container.getId()).exec();
            Map<String, Object> containerProperties = ImmutableMap.<String, Object>builder()
                    .put(Connection.USER, "root")
                    .put(Connection.TARGET, DockerUtil.getDockerHostName(defaultConfig.getHost()))
                    .put(Connection.PORT, containerDetails.getNetworkSettings().getPorts().getBindings().get(sshPort)[0].getHostPortSpec())
                    .put(Connection.KEY_PATH, ClassLoaderUtil.getPathForResource("insecure_key").toString())
                    .put("configuration", ImmutableMap.<String, Object>builder().put("connect_retry", "2").build())
                    .build();
            Thread.sleep(2000);
            test.test(containerProperties);
        } finally {
            dockerClient.removeContainerCmd(container.getId()).withForce(true).exec();
        }
    }

    public static void testWithBasicContainer(TestWithDocker test) throws Exception {
        DockerDaemonConfig defaultConfig = DockerUtil.getDefaultDockerDaemonConfig();
        DockerClient dockerClient = DockerUtil.buildDockerClient(defaultConfig);
        dockerClient.pullImageCmd("toscaruntime/ubuntu-trusty").exec(new PullImageResultCallback()).awaitCompletion();
        CreateContainerResponse container = dockerClient.createContainerCmd("toscaruntime/ubuntu-trusty").withCmd("sleep", "9999")
                .exec();
        try {
            dockerClient.startContainerCmd(container.getId()).exec();
            test.test(ImmutableMap.<String, Object>builder()
                    .put(Connection.TARGET, container.getId())
                    .put(DockerClientConfig.DOCKER_HOST, defaultConfig.getHost())
                    .put(DockerClientConfig.DOCKER_CERT_PATH, defaultConfig.getCertPath())
                    .put(DockerClientConfig.DOCKER_TLS_VERIFY, defaultConfig.getTlsVerify())
                    .build());
        } finally {
            dockerClient.removeContainerCmd(container.getId()).withForce(true).exec();
        }
    }
}
