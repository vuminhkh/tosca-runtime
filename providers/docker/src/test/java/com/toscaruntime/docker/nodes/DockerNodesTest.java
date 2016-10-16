package com.toscaruntime.docker.nodes;

import com.github.dockerjava.core.DockerClientConfig;
import com.google.common.collect.ImmutableMap;
import com.toscaruntime.deployment.DeploymentPersister;
import com.toscaruntime.docker.DockerProviderHook;
import com.toscaruntime.sdk.Provider;
import com.toscaruntime.sdk.SimpleTypeRegistry;
import com.toscaruntime.util.ClassLoaderUtil;
import com.toscaruntime.util.DockerDaemonConfig;
import com.toscaruntime.util.DockerUtil;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class DockerNodesTest {

    @Test
    public void testDocker() throws Throwable {
        DockerTestDeployment testDeployment = new DockerTestDeployment();
        DockerProviderHook providerHook = new DockerProviderHook();
        DockerDaemonConfig dockerDaemonConfig = DockerUtil.getDefaultDockerDaemonConfig();

        Map<String, Object> providerProperties = ImmutableMap.<String, Object>builder()
                .put(DockerClientConfig.DOCKER_HOST, dockerDaemonConfig.getHost())
                .put(DockerClientConfig.DOCKER_CERT_PATH, dockerDaemonConfig.getCertPath())
                .put(DockerClientConfig.DOCKER_TLS_VERIFY, dockerDaemonConfig.getTlsVerify())
                .build();
        List<Provider> providers = Collections.singletonList(new Provider(ImmutableMap.<String, Map<String, Object>>builder().put("default", providerProperties).build(), Collections.singletonList(providerHook)));
        testDeployment.initializeConfig("testDeployment", ClassLoaderUtil.getPathForResource("recipe/"), new HashMap<>(), new SimpleTypeRegistry(new HashMap<>()), new HashMap<>(), providers, new ArrayList<>(), Mockito.mock(DeploymentPersister.class), true);
        try {
            testDeployment.run(testDeployment.install()).waitForCompletion(15, TimeUnit.MINUTES);
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
        } finally {
            testDeployment.run(testDeployment.uninstall()).waitForCompletion(15, TimeUnit.MINUTES);
        }
    }
}
