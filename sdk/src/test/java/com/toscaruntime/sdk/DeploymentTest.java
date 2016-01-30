package com.toscaruntime.sdk;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import com.toscaruntime.sdk.mock.MockDeployment;

import tosca.nodes.Root;

@SuppressWarnings("unchecked")
@RunWith(JUnit4.class)
public class DeploymentTest {

    @Test
    public void testDeployment() {
        MockDeployment mockDeployment = new MockDeployment();
        DeploymentPostConstructor postConstructor = Mockito.mock(DeploymentPostConstructor.class);
        mockDeployment.initializeConfig("test", Paths.get("."), new HashMap<>(), new HashMap<>(), new HashMap<>(), Collections.singletonList(postConstructor), true);
        mockDeployment.install();
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
        mockDeployment.scale("WebServer", 1);
        allWebServers = mockDeployment.getNodeInstancesByNodeName("WebServer");
        allJavas = mockDeployment.getNodeInstancesByNodeName("Java");
        Assert.assertEquals(1, allWebServers.size());
        Assert.assertEquals(1, allJavas.size());
        mockDeployment.scale("WebServer", 3);
        allWebServers = mockDeployment.getNodeInstancesByNodeName("WebServer");
        allJavas = mockDeployment.getNodeInstancesByNodeName("Java");
        Assert.assertEquals(3, allWebServers.size());
        Assert.assertEquals(3, allJavas.size());
        Mockito.verify(postConstructor, Mockito.times(1)).postConstructExtension(Mockito.anyMap(), Mockito.anySet());
        mockDeployment.scale("WebServer", 1);
        mockDeployment.uninstall();
        Assert.assertTrue(mockDeployment.getNodeInstances().isEmpty());
        Assert.assertTrue(mockDeployment.getRelationshipInstances().isEmpty());
    }
}
