package com.toscaruntime.ansible;

import com.google.common.collect.ImmutableMap;
import com.toscaruntime.ansible.connection.AnsibleDockerConnection;
import com.toscaruntime.artifact.SimpleOutputHandler;
import com.toscaruntime.util.ClassLoaderUtil;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.toscaruntime.util.DockerTestUtilities.testWithAnsibleControlMachine;
import static com.toscaruntime.util.DockerTestUtilities.testWithBasicContainer;

@RunWith(JUnit4.class)
public class AnsibleDockerConnectionTest {

    @Test
    public void testDockerConnection() throws Exception {
        testWithAnsibleControlMachine(controlMachineConnection -> testWithBasicContainer(containerProperties -> {
            AnsibleDockerConnection dockerConnection = AnsibleTestUtilities.createDockerConnection(controlMachineConnection, containerProperties);
            Assert.assertEquals(0, (int) dockerConnection.executeCommand("echo \"I'm a good command\""));
            Assert.assertEquals(1, (int) dockerConnection.executeCommand("exit 2"));
            dockerConnection.upload(ClassLoaderUtil.getPathForResource("playbook").toString(), "playbook");
            try (SimpleOutputHandler outputHandler = new SimpleOutputHandler()) {
                Assert.assertEquals(0, (int) dockerConnection.executeRemoteArtifact("playbook/good-playbook.yml", ImmutableMap.<String, Object>builder().put("MY_VARIABLE", "Good variable").build(), outputHandler));
            }
            try (SimpleOutputHandler outputHandler = new SimpleOutputHandler()) {
                Assert.assertNotEquals(0, (int) dockerConnection.executeRemoteArtifact("playbook/bad-playbook.yml", ImmutableMap.<String, Object>builder().put("MY_VARIABLE", "Bad variable").build(), outputHandler));
            }
        }));
    }
}
