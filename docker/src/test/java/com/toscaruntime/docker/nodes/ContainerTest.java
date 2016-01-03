package com.toscaruntime.docker.nodes;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.InternetProtocol;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.toscaruntime.sdk.DeploymentConfig;
import com.toscaruntime.util.ClassLoaderUtil;
import com.toscaruntime.util.DockerUtil;

public class ContainerTest {

    @Test
    public void testCreateContainer() {
        Container container = new Container();
        DeploymentConfig deploymentConfig = new DeploymentConfig();
        deploymentConfig.setDeploymentName("testContainer");
        deploymentConfig.setBootstrap(true);
        deploymentConfig.setRecipePath(ClassLoaderUtil.getPathForResource("recipe/"));
        deploymentConfig.setArtifactsPath(deploymentConfig.getRecipePath().resolve("src").resolve("main").resolve("resources"));
        deploymentConfig.setTopologyResourcePath(deploymentConfig.getArtifactsPath());
        deploymentConfig.setInputs(new HashMap<>());
        container.setId("testContainerId");
        container.setName("testContainerName");
        container.setConfig(deploymentConfig);
        container.setDockerClient(DockerUtil.buildDockerClient("https://192.168.99.100:2376", System.getProperty("user.home") + "/.docker/machine/machines/default"));
        Map<String, Object> properties = ImmutableMap.<String, Object>builder()
                .put("image_id", "phusion/baseimage")
                .put("exposed_ports", Lists.<Map<String, Object>>newArrayList(ImmutableMap.<String, Object>builder().put("port", "80").build()))
                .put("port_mappings", Lists.<Map<String, Object>>newArrayList(ImmutableMap.<String, Object>builder().put("from", "80").put("to", "51000").build()))
                .build();
        container.setProperties(properties);
        Assert.assertEquals(1, container.getExposedPorts().size());
        Assert.assertEquals(1, container.getPortsMapping().size());
        Assert.assertEquals(80, container.getExposedPorts().get(0).getPort());
        Assert.assertEquals(InternetProtocol.TCP, container.getExposedPorts().get(0).getProtocol());
        Assert.assertEquals(51000, (int) container.getPortsMapping().get(80));
        try {
            container.create();
            container.start();
            InspectContainerResponse containerInspect = container.getDockerClient().inspectContainerCmd(container.getContainerId()).exec();
            Assert.assertTrue(!containerInspect.getNetworkSettings().getPorts().getBindings().isEmpty());
            Assert.assertEquals(80, containerInspect.getNetworkSettings().getPorts().getBindings().keySet().iterator().next().getPort());
            Assert.assertEquals(InternetProtocol.TCP, containerInspect.getNetworkSettings().getPorts().getBindings().keySet().iterator().next().getProtocol());
            Assert.assertNotNull(container.getAttribute("ip_address"));
            Map<String, String> outputs = container.execute("test", "testScript.sh", ImmutableMap.<String, Object>builder().put("HELLO_ARGS", "WORLD").build());
            Assert.assertEquals("Hello WORLD", outputs.get("OUTPUT_TEST"));
        } finally {
            container.stop();
            container.delete();
        }
    }
}
