package com.toscaruntime.docker.nodes;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.InternetProtocol;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.toscaruntime.deployment.DeploymentPersister;
import com.toscaruntime.sdk.Deployment;
import com.toscaruntime.sdk.model.DeploymentConfig;
import com.toscaruntime.util.ClassLoaderUtil;
import com.toscaruntime.util.DockerDaemonConfig;
import com.toscaruntime.util.DockerUtil;

@RunWith(JUnit4.class)
public class ContainerTest {

    private Logger logger = LoggerFactory.getLogger(ContainerTest.class);

    private Deployment mockDeployment() {
        Deployment deployment = Mockito.mock(Deployment.class);
        Mockito.when(deployment.getDeploymentPersister()).thenReturn(Mockito.mock(DeploymentPersister.class));
        return deployment;
    }

    private Container createContainer(String imageId) throws MalformedURLException {
        Container container = new Container();
        container.setDeployment(mockDeployment());
        DeploymentConfig deploymentConfig = new DeploymentConfig();
        deploymentConfig.setDeploymentName("testContainer");
        deploymentConfig.setBootstrap(true);
        deploymentConfig.setRecipePath(ClassLoaderUtil.getPathForResource("recipe/"));
        deploymentConfig.setArtifactsPath(deploymentConfig.getRecipePath().resolve("src").resolve("main").resolve("resources"));
        deploymentConfig.setTopologyResourcePath(deploymentConfig.getArtifactsPath());
        deploymentConfig.setInputs(new HashMap<>());
        container.setIndex(1);
        container.setName("testContainerName");
        container.setConfig(deploymentConfig);
        DockerDaemonConfig config = DockerUtil.getDefaultDockerDaemonConfig();
        container.setDockerClient(DockerUtil.buildDockerClient(config.getUrl(), config.getCertPath()));
        Map<String, Object> properties = ImmutableMap.<String, Object>builder()
                .put("image_id", imageId)
                .put("tag", "latest")
                .put("interactive", "true")
                .put("commands", Lists.newArrayList("bash", "-l"))
                .put("exposed_ports", Lists.<Map<String, Object>>newArrayList(ImmutableMap.<String, Object>builder().put("port", "80").build()))
                .put("port_mappings", Lists.<Map<String, Object>>newArrayList(ImmutableMap.<String, Object>builder().put("from", "80").put("to", "50000").build()))
                .build();
        container.setProperties(properties);
        Assert.assertEquals(1, container.getExposedPorts().size());
        Assert.assertEquals(1, container.getPortsMapping().size());
        Assert.assertEquals(80, container.getExposedPorts().get(0).getPort());
        Assert.assertEquals(InternetProtocol.TCP, container.getExposedPorts().get(0).getProtocol());
        Assert.assertEquals(50000, (int) container.getPortsMapping().get(80));
        return container;
    }

    private void cleanContainer(Container container) {
        try {
            container.stop();
        } catch (Exception e) {
            // Ignore
        }
        try {
            container.delete();
        } catch (Exception e) {
            // Ignore
        }
    }

    @Test
    public void testContainerWithNetwork() throws MalformedURLException {
        Container container = createContainer("toscaruntime/ubuntu-trusty");
        Network network = new Network();
        network.setDeployment(mockDeployment());
        network.setDockerClient(container.getDockerClient());
        network.setIndex(1);
        network.setName("dockerNet");
        network.setProperties(ImmutableMap.<String, Object>builder().put("network_name", "dockerNet").put("cidr", "10.67.79.0/24").build());
        container.setNetworks(Sets.newHashSet(network));
        try {
            network.create();
            container.create();
            container.start();
            Map<String, String> outputs = container.execute("test", "testScript.sh", ImmutableMap.<String, Object>builder().put("HELLO_ARGS", "I'm John").build(), ImmutableMap.<String, String>builder().put("conf_artifact", "path/to/confDir").build());
            Assert.assertEquals("Hello I'm John", outputs.get("OUTPUT_TEST"));
            Assert.assertNotNull(network.getNetworkId());
        } catch (Exception e) {
            logger.error("Error in test", e);
            throw e;
        } finally {
            cleanContainer(container);
            network.delete();
        }
    }

    @Test
    public void testCreateContainer() throws MalformedURLException {
        Container container = createContainer("toscaruntime/ubuntu-trusty");
        try {
            container.create();
            container.start();
            InspectContainerResponse containerInspect = container.getDockerClient().inspectContainerCmd(container.getContainerId()).exec();
            Assert.assertTrue(!containerInspect.getNetworkSettings().getPorts().getBindings().isEmpty());
            Assert.assertEquals(80, containerInspect.getNetworkSettings().getPorts().getBindings().keySet().iterator().next().getPort());
            Assert.assertEquals(InternetProtocol.TCP, containerInspect.getNetworkSettings().getPorts().getBindings().keySet().iterator().next().getProtocol());
            Assert.assertNotNull(container.getAttribute("ip_address"));
            Map<String, String> outputs = container.execute("test", "testScript.sh", ImmutableMap.<String, Object>builder().put("HELLO_ARGS", "I'm John").build(), ImmutableMap.<String, String>builder().put("conf_artifact", "path/to/confDir").build());
            Assert.assertEquals("Hello I'm John", outputs.get("OUTPUT_TEST"));
            Assert.assertEquals(Container.RECIPE_LOCATION + "/path/to/confDir", outputs.get("conf_artifact"));
        } catch (Exception e) {
            logger.error("Error in test", e);
            throw e;
        } finally {
            cleanContainer(container);
        }
    }
}
