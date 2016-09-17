package com.toscaruntime.aws.nodes;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.toscaruntime.aws.AWSProviderHook;
import com.toscaruntime.deployment.DeploymentPersister;
import com.toscaruntime.exception.deployment.execution.InvalidOperationExecutionException;
import com.toscaruntime.util.ClassLoaderUtil;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AWSNodesTest {

//    @Ignore
//    @Test
//    public void testAWS() throws Throwable {
//        if (StringUtils.isBlank(System.getenv("AWS_ACCESS_KEY_ID"))) {
//            throw new IllegalArgumentException("Env var AWS_ACCESS_KEY_ID is not set");
//        }
//        if (StringUtils.isBlank(System.getenv("AWS_ACCESS_KEY_SECRET"))) {
//            throw new IllegalArgumentException("Env var AWS_ACCESS_KEY_SECRET is not set");
//        }
//        if (StringUtils.isBlank(System.getenv("AWS_REGION"))) {
//            throw new IllegalArgumentException("Env var AWS_REGION is not set");
//        }
//        if (StringUtils.isBlank(System.getenv("AWS_KEY_PATH"))) {
//            throw new IllegalArgumentException("Env var AWS_KEY_PATH is not set");
//        }
//        if (StringUtils.isBlank(System.getenv("AWS_KEY_NAME"))) {
//            throw new IllegalArgumentException("Env var AWS_KEY_NAME is not set");
//        }
//        AWSProviderHook awsProviderHook = new AWSProviderHook();
//        AWSTestDeployment awsTestDeployment = new AWSTestDeployment();
//        Map<String, String> providerProperties = ImmutableMap.<String, String>builder()
//                .put("access_key_id", System.getenv("AWS_ACCESS_KEY_ID"))
//                .put("access_key_secret", System.getenv("AWS_ACCESS_KEY_SECRET"))
//                .put("region", System.getenv("AWS_REGION")).build();
////        awsTestDeployment.initializeConfig("testDeployment", ClassLoaderUtil.getPathForResource("recipe/"), new HashMap<>(), providerProperties, new HashMap<>(), awsProviderHook, new ArrayList<>(), Mockito.mock(DeploymentPersister.class), true);
//        try {
//            awsTestDeployment.run(awsTestDeployment.install()).waitForCompletion(15, TimeUnit.MINUTES);
//            Instance compute = awsTestDeployment.getNodeInstancesByType(Instance.class).iterator().next();
//            Assert.assertNotNull(compute.getAttributeAsString("public_ip_address"));
//            Assert.assertNotNull(compute.getAttributeAsString("ip_address"));
//            Assert.assertNotNull(compute.getAttributeAsString("provider_resource_id"));
//            Assert.assertNotNull(compute.getAttributeAsString("provider_resource_name"));
//            Assert.assertEquals("Compute_1", compute.getAttributeAsString("tosca_id"));
//            Assert.assertEquals("Compute", compute.getAttributeAsString("tosca_name"));
//
//            PublicNetwork externalNetwork = awsTestDeployment.getNodeInstancesByType(PublicNetwork.class).iterator().next();
//            Assert.assertEquals("ExternalNetwork_1", externalNetwork.getAttributeAsString("tosca_id"));
//            Assert.assertEquals("ExternalNetwork", externalNetwork.getAttributeAsString("tosca_name"));
//
//            Map<String, String> outputs = compute.execute("testOutput", "testScript.sh", ImmutableMap.<String, Object>builder().put("HELLO_ARGS", "I'm John").build(), new HashMap<>());
//            Assert.assertEquals("Hello I'm John", outputs.get("OUTPUT_TEST"));
//            try {
//                compute.execute("testError", "testErrorScript.sh", Maps.newHashMap(), new HashMap<>());
//                Assert.fail("testErrorScript.sh should trigger error");
//            } catch (InvalidOperationExecutionException ignored) {
//                // It's what's expected
//            }
//        } finally {
//            awsTestDeployment.run(awsTestDeployment.uninstall()).waitForCompletion(15, TimeUnit.MINUTES);
//        }
//    }
}
