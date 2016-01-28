package com.toscaruntime.openstack.nodes;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.toscaruntime.openstack.OpenstackDeploymentPostConstructor;
import com.toscaruntime.util.ClassLoaderUtil;

@RunWith(JUnit4.class)
public class OpenstackNodesTest {

    @Test
    public void testOpenstack() {
        OpenstackTestDeployment testDeployment = new OpenstackTestDeployment();
        testDeployment.initialize("testDeployment", ClassLoaderUtil.getPathForResource("recipe/"), Maps.newHashMap(), true);
        OpenstackDeploymentPostConstructor postConstructor = new OpenstackDeploymentPostConstructor();
        Map<String, String> providerProperties = ImmutableMap.<String, String>builder()
                .put("keystone_url", "http://128.136.179.2:5000/v2.0")
                .put("user", "facebook1389662728")
                .put("region", "RegionOne")
                .put("password", "mqAgNPA2c6VDjoOD")
                .put("tenant", "facebook1389662728").build();
        postConstructor.postConstruct(testDeployment, providerProperties, Maps.newHashMap());
        try {
            testDeployment.install();
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

            Map<String, String> outputs = compute.execute("testOutput", "testScript.sh", ImmutableMap.<String, Object>builder().put("HELLO_ARGS", "I'm John").build());
            Assert.assertEquals("Hello I'm John", outputs.get("OUTPUT_TEST"));

            outputs = compute.execute("testJava", "javaHelp.sh", Maps.newHashMap());
            Assert.assertNotNull(outputs.get("JAVA_HELP"));
        } finally {
            testDeployment.uninstall();
        }
    }
}
