package com.toscaruntime.aws.nodes;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.toscaruntime.aws.AWSProviderHook;
import com.toscaruntime.deployment.DeploymentPersister;
import com.toscaruntime.sdk.Provider;
import com.toscaruntime.sdk.ProviderHook;
import com.toscaruntime.sdk.SimpleTypeRegistry;
import com.toscaruntime.test.ConfigLoader;
import com.toscaruntime.util.ClassLoaderUtil;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AWSNodesTest {

    @Test
    public void testAWS() throws Throwable {
        AWSProviderHook awsProviderHook = new AWSProviderHook();
        AWSTestDeployment awsTestDeployment = new AWSTestDeployment();
        Map<String, Object> providerProperties = ConfigLoader.loadConfig("aws");
        Map<String, Object> inputs = ConfigLoader.loadInput("aws");
        List<Provider> providers = ImmutableList.<Provider>builder().add(new Provider(ImmutableMap.<String, Map<String, Object>>builder().put("default", providerProperties).build(), ImmutableList.<ProviderHook>builder().add(awsProviderHook).build())).build();
        awsTestDeployment.initializeConfig("testDeployment", ClassLoaderUtil.getPathForResource("recipe/"), inputs, new SimpleTypeRegistry(new HashMap<>()), new HashMap<>(), providers, new ArrayList<>(), Mockito.mock(DeploymentPersister.class), true);
        try {
            awsTestDeployment.run(awsTestDeployment.install()).waitForCompletion(15, TimeUnit.MINUTES);
            Instance compute = awsTestDeployment.getNodeInstancesByType(Instance.class).iterator().next();
            Assert.assertNotNull(compute.getAttributeAsString("public_ip_address"));
            Assert.assertNotNull(compute.getAttributeAsString("ip_address"));
            Assert.assertNotNull(compute.getAttributeAsString("provider_resource_id"));
            Assert.assertNotNull(compute.getAttributeAsString("provider_resource_name"));
            Assert.assertEquals("Compute_1", compute.getAttributeAsString("tosca_id"));
            Assert.assertEquals("Compute", compute.getAttributeAsString("tosca_name"));

            PublicNetwork externalNetwork = awsTestDeployment.getNodeInstancesByType(PublicNetwork.class).iterator().next();
            Assert.assertEquals("ExternalNetwork_1", externalNetwork.getAttributeAsString("tosca_id"));
            Assert.assertEquals("ExternalNetwork", externalNetwork.getAttributeAsString("tosca_name"));
        } finally {
            awsTestDeployment.run(awsTestDeployment.uninstall()).waitForCompletion(15, TimeUnit.MINUTES);
        }
    }
}
