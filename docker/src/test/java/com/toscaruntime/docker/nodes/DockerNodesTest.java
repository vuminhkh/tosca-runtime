package com.toscaruntime.docker.nodes;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableMap;
import com.toscaruntime.docker.DockerDeploymentPostConstructor;
import com.toscaruntime.sdk.DeploymentPostConstructor;
import com.toscaruntime.util.ClassLoaderUtil;
import com.toscaruntime.util.DockerDaemonConfig;
import com.toscaruntime.util.DockerUtil;

//@Ignore
@RunWith(JUnit4.class)
public class DockerNodesTest {

    @Test
    public void testDocker() throws Throwable {
        DockerTestDeployment testDeployment = new DockerTestDeployment();
        DockerDeploymentPostConstructor postConstructor = new DockerDeploymentPostConstructor();
        DockerDaemonConfig dockerDaemonConfig = DockerUtil.getDefaultDockerDaemonConfig();

        Map<String, String> providerProperties = ImmutableMap.<String, String>builder()
                .put(DockerUtil.DOCKER_URL_KEY, dockerDaemonConfig.getUrl())
                .put(DockerUtil.DOCKER_CERT_PATH_KEY, dockerDaemonConfig.getCertPath())
                .build();
        testDeployment.initializeConfig("testDeployment", ClassLoaderUtil.getPathForResource("recipe/"), new HashMap<>(), providerProperties, new HashMap<>(), Collections.<DeploymentPostConstructor>singletonList(postConstructor), true);
        try {
            testDeployment.install().waitForCompletion(15, TimeUnit.MINUTES);
            Container compute = testDeployment.getNodeInstancesByType(Container.class).iterator().next();
            Assert.assertNotNull(compute.getAttributeAsString("public_ip_address"));
            Assert.assertNotNull(compute.getAttributeAsString("ip_address"));
            Assert.assertNotNull(compute.getAttributeAsString("provider_resource_id"));
            Assert.assertNotNull(compute.getAttributeAsString("provider_resource_name"));
            Assert.assertEquals("Compute_1", compute.getAttributeAsString("tosca_id"));
            Assert.assertEquals("Compute", compute.getAttributeAsString("tosca_name"));

            Network network = testDeployment.getNodeInstancesByType(Network.class).iterator().next();
            Assert.assertNotNull(network.getAttributeAsString("provider_resource_id"));
            Assert.assertEquals("dockerNet", network.getAttributeAsString("provider_resource_name"));
            Assert.assertEquals("Network_1", network.getAttributeAsString("tosca_id"));
            Assert.assertEquals("Network", network.getAttributeAsString("tosca_name"));

            DeletableVolume volume = testDeployment.getNodeInstancesByType(DeletableVolume.class).iterator().next();
            Assert.assertEquals("toscaruntimeTestVolume", volume.getAttributeAsString("provider_resource_id"));
            Assert.assertEquals("toscaruntimeTestVolume", volume.getAttributeAsString("provider_resource_name"));
            Assert.assertEquals("Volume_1_1", volume.getAttributeAsString("tosca_id"));
            Assert.assertEquals("Volume", volume.getAttributeAsString("tosca_name"));

            Map<String, String> outputs = compute.execute("testWriteToVolume", "testWriteToVolume.sh", ImmutableMap.<String, Object>builder().put("FILE_CONTENT", "A great content").build(), new HashMap<>());
            Assert.assertEquals("A great content", outputs.get("WRITTEN"));
        } finally {
            testDeployment.uninstall().waitForCompletion(15, TimeUnit.MINUTES);
        }
    }
}
