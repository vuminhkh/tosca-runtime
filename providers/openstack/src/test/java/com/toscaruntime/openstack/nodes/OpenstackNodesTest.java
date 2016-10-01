package com.toscaruntime.openstack.nodes;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.toscaruntime.deployment.DeploymentPersister;
import com.toscaruntime.openstack.OpenstackProviderHook;
import com.toscaruntime.sdk.Provider;
import com.toscaruntime.sdk.ProviderHook;
import com.toscaruntime.sdk.SimpleTypeRegistry;
import com.toscaruntime.test.ConfigLoader;
import com.toscaruntime.util.ClassLoaderUtil;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import tosca.constants.RelationshipInstanceState;
import tosca.relationships.AttachTo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class OpenstackNodesTest {

    @Test
    public void testOpenstack() throws Throwable {
        OpenstackTestDeployment testDeployment = new OpenstackTestDeployment();
        OpenstackProviderHook providerHook = new OpenstackProviderHook();
        Map<String, Object> providerProperties = ConfigLoader.loadConfig("openstack");
        List<Provider> providers = ImmutableList.<Provider>builder().add(new Provider(ImmutableMap.<String, Map<String, Object>>builder().put("default", providerProperties).build(), ImmutableList.<ProviderHook>builder().add(providerHook).build())).build();
        Map<String, Object> inputs = ConfigLoader.loadInput("openstack");
        testDeployment.initializeConfig("testDeployment", ClassLoaderUtil.getPathForResource("recipe/"), inputs, new SimpleTypeRegistry(new HashMap<>()), new HashMap<>(), providers, new ArrayList<>(), Mockito.mock(DeploymentPersister.class), true);
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
            Assert.assertEquals(inputs.get("external_network_name"), externalNetwork.getAttributeAsString("provider_resource_name"));
            Assert.assertEquals("ExternalNetwork_1", externalNetwork.getAttributeAsString("tosca_id"));
            Assert.assertEquals("ExternalNetwork", externalNetwork.getAttributeAsString("tosca_name"));

            DeletableVolume volume = testDeployment.getNodeInstancesByType(DeletableVolume.class).iterator().next();
            Assert.assertNotNull(volume.getAttributeAsString("device"));
            Set<AttachTo> attachedTos = testDeployment.getRelationshipInstancesByNamesAndType("Volume", "Compute", AttachTo.class);
            Assert.assertEquals(1, attachedTos.size());
            Assert.assertEquals(RelationshipInstanceState.ESTABLISHED, attachedTos.iterator().next().getState());
        } finally {
            testDeployment.run(testDeployment.uninstall()).waitForCompletion(15, TimeUnit.MINUTES);
        }
    }
}
