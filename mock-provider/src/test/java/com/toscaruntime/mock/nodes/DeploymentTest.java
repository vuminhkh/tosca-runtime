package com.toscaruntime.mock.nodes;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import com.toscaruntime.deployment.DeploymentPersister;
import com.toscaruntime.mock.MockProviderHook;
import com.toscaruntime.sdk.ProviderHook;

import tosca.constants.InstanceState;
import tosca.constants.RelationshipInstanceState;
import tosca.nodes.Root;

@SuppressWarnings("unchecked")
@RunWith(JUnit4.class)
public class DeploymentTest {

    private void assertDeployment(MockDeployment mockDeployment) {
        for (Root instance : mockDeployment.getNodeInstances().values()) {
            Assert.assertEquals("Failure for " + instance, InstanceState.STARTED, instance.getState());
        }
        for (tosca.relationships.Root relationship : mockDeployment.getRelationshipInstances()) {
            Assert.assertEquals("Failure for " + relationship, RelationshipInstanceState.ESTABLISHED, relationship.getState());
        }
    }

    @Test
    public void testDeployment() throws Throwable {
        MockDeployment mockDeployment = new MockDeployment();
        try {
            ProviderHook providerHook = new MockProviderHook();
            mockDeployment.initializeConfig("test", Paths.get("."), new HashMap<>(), new HashMap<>(), new HashMap<>(), providerHook, Mockito.mock(DeploymentPersister.class), true);
            mockDeployment.run(mockDeployment.install()).waitForCompletion(2, TimeUnit.MINUTES);
            Set<Root> allWebServers = mockDeployment.getNodeInstancesByNodeName("WebServer");
            Set<Root> allJavas = mockDeployment.getNodeInstancesByNodeName("Java");
            Assert.assertEquals(2, allWebServers.size());
            Assert.assertEquals(2, allJavas.size());
            assertDeployment(mockDeployment);

            mockDeployment.run(mockDeployment.scale("WebServer", 1)).waitForCompletion(2, TimeUnit.MINUTES);
            allWebServers = mockDeployment.getNodeInstancesByNodeName("WebServer");
            allJavas = mockDeployment.getNodeInstancesByNodeName("Java");
            Assert.assertEquals(1, allWebServers.size());
            Assert.assertEquals(1, allJavas.size());
            assertDeployment(mockDeployment);

            mockDeployment.run(mockDeployment.scale("WebServer", 3)).waitForCompletion(2, TimeUnit.MINUTES);
            allWebServers = mockDeployment.getNodeInstancesByNodeName("WebServer");
            allJavas = mockDeployment.getNodeInstancesByNodeName("Java");
            Assert.assertEquals(3, allWebServers.size());
            Assert.assertEquals(3, allJavas.size());
            assertDeployment(mockDeployment);

            mockDeployment.run(mockDeployment.scale("WebServer", 1)).waitForCompletion(2, TimeUnit.MINUTES);
            allWebServers = mockDeployment.getNodeInstancesByNodeName("WebServer");
            allJavas = mockDeployment.getNodeInstancesByNodeName("Java");
            Assert.assertEquals(1, allWebServers.size());
            Assert.assertEquals(1, allJavas.size());
            assertDeployment(mockDeployment);
        } finally {
            mockDeployment.run(mockDeployment.uninstall()).waitForCompletion(2, TimeUnit.MINUTES);
            Assert.assertTrue(mockDeployment.getNodeInstances().isEmpty());
            Assert.assertTrue(mockDeployment.getRelationshipInstances().isEmpty());
        }
    }
}
