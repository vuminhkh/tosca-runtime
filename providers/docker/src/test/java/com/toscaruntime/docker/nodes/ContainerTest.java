package com.toscaruntime.docker.nodes;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.InternetProtocol;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.toscaruntime.deployment.DeploymentPersister;
import com.toscaruntime.docker.DockerProviderConnection;
import com.toscaruntime.sdk.model.DeploymentConfig;
import com.toscaruntime.util.ClassLoaderUtil;
import com.toscaruntime.util.DockerDaemonConfig;
import com.toscaruntime.util.DockerUtil;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

@RunWith(JUnit4.class)
public class ContainerTest {

    private Logger logger = LoggerFactory.getLogger(ContainerTest.class);

    private Container createContainer(String imageId) throws MalformedURLException {
        Container container = new Container();
        DeploymentConfig deploymentConfig = new DeploymentConfig();
        deploymentConfig.setDeploymentName("testContainer");
        deploymentConfig.setBootstrap(true);
        deploymentConfig.setArtifactsPath(ClassLoaderUtil.getPathForResource("recipe/").resolve("src").resolve("main").resolve("resources"));
        deploymentConfig.setTopologyResourcePath(deploymentConfig.getArtifactsPath());
        deploymentConfig.setInputs(new HashMap<>());
        deploymentConfig.setDeploymentPersister(Mockito.mock(DeploymentPersister.class));
        container.setIndex(1);
        container.setName("testContainerName");
        container.setConfig(deploymentConfig);
        DockerDaemonConfig config = DockerUtil.getDefaultDockerDaemonConfig();
        DockerProviderConnection connection = Mockito.mock(DockerProviderConnection.class);
        Mockito.when(connection.getDockerClient()).thenReturn(DockerUtil.buildDockerClient(config));
        container.setConnection(connection);
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
        network.setIndex(1);
        network.setName("dockerNet");
        network.setDockerClient(container.getConnection().getDockerClient());
        network.setConfig(container.getConfig());
        network.setProperties(ImmutableMap.<String, Object>builder().put("network_name", "dockerNet").put("cidr", "10.67.79.0/24").build());
        container.setNetworks(Sets.newHashSet(network));
        try {
            network.create();
            container.create();
            container.start();
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
            InspectContainerResponse containerInspect = container.getConnection().getDockerClient().inspectContainerCmd(container.getContainerId()).exec();
            Assert.assertTrue(!containerInspect.getNetworkSettings().getPorts().getBindings().isEmpty());
            Assert.assertEquals(80, containerInspect.getNetworkSettings().getPorts().getBindings().keySet().iterator().next().getPort());
            Assert.assertEquals(InternetProtocol.TCP, containerInspect.getNetworkSettings().getPorts().getBindings().keySet().iterator().next().getProtocol());
            Assert.assertNotNull(container.getAttribute("ip_address"));
        } catch (Exception e) {
            logger.error("Error in test", e);
            throw e;
        } finally {
            cleanContainer(container);
        }
    }
}
