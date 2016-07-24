package com.toscaruntime.openstack.nodes;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.toscaruntime.deployment.DeploymentPersister;
import com.toscaruntime.exception.deployment.execution.InvalidOperationExecutionException;
import com.toscaruntime.openstack.OpenstackProviderHook;
import com.toscaruntime.util.ClassLoaderUtil;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import tosca.constants.RelationshipInstanceState;
import tosca.relationships.AttachTo;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class OpenstackNodesTest {

    @Ignore
    @Test
    public void testOpenstack() throws Throwable {
        OpenstackTestDeployment testDeployment = new OpenstackTestDeployment();
        OpenstackProviderHook providerHook = new OpenstackProviderHook();
        Map<String, String> providerProperties = ImmutableMap.<String, String>builder()
                .put("keystone_url", "http://128.136.179.2:5000/v2.0")
                .put("user", "facebook1389662728")
                .put("region", "RegionOne")
                .put("password", "mqAgNPA2c6VDjoOD")
                .put("tenant", "facebook1389662728").build();
        testDeployment.initializeConfig("testDeployment", ClassLoaderUtil.getPathForResource("recipe/"), new HashMap<>(), providerProperties, new HashMap<>(), providerHook, Mockito.mock(DeploymentPersister.class), true);
        try {
            testDeployment.run(testDeployment.install()).waitForCompletion(15, TimeUnit.MINUTES);
            Compute compute = testDeployment.getNodeInstancesByType(Compute.class).iterator().next();
            Assert.assertNotNull(compute.getAttributeAsString("public_ip_address"));
            Assert.assertNotNull(compute.getAttributeAsString("ip_address"));
            Assert.assertNotNull(compute.getAttributeAsString("provider_resource_id"));
            Assert.assertNotNull(compute.getAttributeAsString("provider_resource_name"));
            Assert.assertEquals("Compute_1", compute.getAttributeAsString("tosca_id"));
            Assert.assertEquals("Compute", compute.getAttributeAsString("tosca_name"));

            Network network = testDeployment.getNodeInstancesByType(Network.class).iterator().next();
            Assert.assertNotNull(network.getAttributeAsString("provider_resource_id"));
            Assert.assertEquals("test-network", network.getAttributeAsString("provider_resource_name"));
            Assert.assertEquals("Network_1", network.getAttributeAsString("tosca_id"));
            Assert.assertEquals("Network", network.getAttributeAsString("tosca_name"));

            ExternalNetwork externalNetwork = testDeployment.getNodeInstancesByType(ExternalNetwork.class).iterator().next();
            Assert.assertNotNull(externalNetwork.getAttributeAsString("provider_resource_id"));
            Assert.assertEquals("public", externalNetwork.getAttributeAsString("provider_resource_name"));
            Assert.assertEquals("ExternalNetwork_1", externalNetwork.getAttributeAsString("tosca_id"));
            Assert.assertEquals("ExternalNetwork", externalNetwork.getAttributeAsString("tosca_name"));

            DeletableVolume volume = testDeployment.getNodeInstancesByType(DeletableVolume.class).iterator().next();
            Assert.assertNotNull(volume.getAttributeAsString("device"));
            Set<AttachTo> attachedTos = testDeployment.getRelationshipInstancesByNamesAndType("Volume", "Compute", AttachTo.class);
            Assert.assertEquals(1, attachedTos.size());
            Assert.assertEquals(RelationshipInstanceState.ESTABLISHED, attachedTos.iterator().next().getState());

            Map<String, String> outputs = compute.execute("testOutput", "testScript.sh", ImmutableMap.<String, Object>builder().put("HELLO_ARGS", "I'm John").build(), new HashMap<>());
            Assert.assertEquals("Hello I'm John", outputs.get("OUTPUT_TEST"));
            try {
                compute.execute("testError", "testErrorScript.sh", Maps.newHashMap(), new HashMap<>());
                Assert.fail("testErrorScript.sh should trigger error");
            } catch (InvalidOperationExecutionException ignored) {
                // It's what's expected
            }
        } finally {
            testDeployment.run(testDeployment.uninstall()).waitForCompletion(15, TimeUnit.MINUTES);
        }
    }
}
