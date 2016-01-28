package com.toscaruntime.sdk;

import java.nio.file.Paths;
import java.util.HashMap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.toscaruntime.sdk.mock.MockDeployment;

@RunWith(JUnit4.class)
public class DeploymentTest {

    @Test
    public void testDeployment() {
        MockDeployment mockDeployment = new MockDeployment();
        mockDeployment.initialize("test", Paths.get("."), new HashMap<>(), true);
        mockDeployment.install();
        mockDeployment.scale("WebServer", 1);
        mockDeployment.scale("WebServer", 3);
        mockDeployment.scale("WebServer", 1);
        mockDeployment.uninstall();
    }
}
