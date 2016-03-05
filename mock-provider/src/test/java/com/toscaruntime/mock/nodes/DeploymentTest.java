package com.toscaruntime.mock.nodes;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import com.toscaruntime.deployment.DeploymentPersister;
import com.toscaruntime.sdk.DeploymentPostConstructor;

import tosca.nodes.Root;

@SuppressWarnings("unchecked")
@RunWith(JUnit4.class)
public class DeploymentTest {

    @Test
    public void testDeployment() throws Throwable {
        MockDeployment mockDeployment = new MockDeployment();
        DeploymentPostConstructor postConstructor = Mockito.mock(DeploymentPostConstructor.class);
        mockDeployment.initializeConfig("test", Paths.get("."), new HashMap<>(), new HashMap<>(), new HashMap<>(), Collections.singletonList(postConstructor), Mockito.mock(DeploymentPersister.class), true);
        mockDeployment.install().waitForCompletion(2, TimeUnit.MINUTES);
        Set<Root> allWebServers = mockDeployment.getNodeInstancesByNodeName("WebServer");
        Set<Root> allJavas = mockDeployment.getNodeInstancesByNodeName("Java");
        Assert.assertEquals(2, allWebServers.size());
        Assert.assertEquals(2, allJavas.size());
        for (Root webServer : allWebServers) {
            Assert.assertEquals("started", webServer.getState());
        }
        for (Root java : allJavas) {
            Assert.assertEquals("started", java.getState());
        }
        mockDeployment.scale("WebServer", 1).waitForCompletion(2, TimeUnit.MINUTES);
        allWebServers = mockDeployment.getNodeInstancesByNodeName("WebServer");
        allJavas = mockDeployment.getNodeInstancesByNodeName("Java");
        Assert.assertEquals(1, allWebServers.size());
        Assert.assertEquals(1, allJavas.size());
        mockDeployment.scale("WebServer", 3).waitForCompletion(2, TimeUnit.MINUTES);
        allWebServers = mockDeployment.getNodeInstancesByNodeName("WebServer");
        allJavas = mockDeployment.getNodeInstancesByNodeName("Java");
        Assert.assertEquals(3, allWebServers.size());
        Assert.assertEquals(3, allJavas.size());
        Mockito.verify(postConstructor, Mockito.times(1)).postConstructInstances(Mockito.anyMap(), Mockito.anySet());
        mockDeployment.scale("WebServer", 1).waitForCompletion(2, TimeUnit.MINUTES);
        allWebServers = mockDeployment.getNodeInstancesByNodeName("WebServer");
        allJavas = mockDeployment.getNodeInstancesByNodeName("Java");
        Assert.assertEquals(1, allWebServers.size());
        Assert.assertEquals(1, allJavas.size());
        mockDeployment.uninstall().waitForCompletion(2, TimeUnit.MINUTES);
        Assert.assertTrue(mockDeployment.getNodeInstances().isEmpty());
        Assert.assertTrue(mockDeployment.getRelationshipInstances().isEmpty());
    }
}
