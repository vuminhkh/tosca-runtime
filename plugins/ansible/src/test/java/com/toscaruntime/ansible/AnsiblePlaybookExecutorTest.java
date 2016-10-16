package com.toscaruntime.ansible;

import com.google.common.collect.ImmutableMap;
import com.toscaruntime.ansible.connection.AnsibleDockerConnection;
import com.toscaruntime.ansible.connection.AnsibleSSHConnection;
import com.toscaruntime.ansible.executor.AnsiblePlaybookExecutor;
import com.toscaruntime.artifact.Executor;
import com.toscaruntime.util.ClassLoaderUtil;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collections;
import java.util.Map;

import static com.toscaruntime.util.DockerTestUtilities.testWithAnsibleControlMachine;
import static com.toscaruntime.util.DockerTestUtilities.testWithBasicContainer;
import static com.toscaruntime.util.DockerTestUtilities.testWithSSHEnabledContainer;

@RunWith(JUnit4.class)
public class AnsiblePlaybookExecutorTest {

    @Test
    public void testPlaybookWithSSHConnection() throws Exception {
        testWithAnsibleControlMachine(controlMachineConnection -> testWithSSHEnabledContainer(containerProperties -> {
            AnsibleSSHConnection sshConnection = AnsibleTestUtilities.createSSHConnection(controlMachineConnection, containerProperties);

            AnsiblePlaybookExecutor playbookExecutor = new AnsiblePlaybookExecutor();
            playbookExecutor.initialize(sshConnection, ImmutableMap.<String, Object>builder()
                    .put(Executor.LOCAL_RECIPE_LOCATION_KEY, ClassLoaderUtil.getPathForResource("playbook").toString())
                    .build()
            );
            playbookExecutor.executeArtifact("test", "test", "good-playbook.yml", ImmutableMap.<String, Object>builder().put("MY_VARIABLE", "Good variable").build(), Collections.emptyMap());
            try {
                playbookExecutor.executeArtifact("test", "test", "bad-playbook.yml", ImmutableMap.<String, Object>builder().put("MY_VARIABLE", "Bad variable").build(), Collections.emptyMap());
                Assert.fail("This playbook should not succeed");
            } catch (Exception ignored) {

            }
            Map<String, Object> outputs = playbookExecutor.executeArtifact("test", "test", "output-playbook.yml", Collections.emptyMap(), Collections.emptyMap());
            Assert.assertTrue(outputs.containsKey("MY_OUTPUT"));
            Assert.assertEquals("Great output", outputs.get("MY_OUTPUT"));

            playbookExecutor.executeArtifact("test", "test", "artifact-playbook.yml", Collections.emptyMap(), ImmutableMap.<String, String>builder().put("MY_CONF", "conf/conf.properties").build());
        }));
    }

    @Test
    public void testPlaybookWithDockerConnection() throws Exception {
        testWithAnsibleControlMachine(controlMachineConnection -> testWithBasicContainer(containerProperties -> {
            AnsibleDockerConnection dockerConnection = AnsibleTestUtilities.createDockerConnection(controlMachineConnection, containerProperties);

            AnsiblePlaybookExecutor playbookExecutor = new AnsiblePlaybookExecutor();
            playbookExecutor.initialize(dockerConnection, ImmutableMap.<String, Object>builder()
                    .put(Executor.LOCAL_RECIPE_LOCATION_KEY, ClassLoaderUtil.getPathForResource("playbook").toString())
                    .build()
            );
            playbookExecutor.executeArtifact("test", "test", "good-playbook.yml", ImmutableMap.<String, Object>builder().put("MY_VARIABLE", "Good variable").build(), Collections.emptyMap());
            try {
                playbookExecutor.executeArtifact("test", "test", "bad-playbook.yml", ImmutableMap.<String, Object>builder().put("MY_VARIABLE", "Bad variable").build(), Collections.emptyMap());
                Assert.fail("This playbook should not succeed");
            } catch (Exception ignored) {

            }
            Map<String, Object> outputs = playbookExecutor.executeArtifact("test", "test", "output-playbook.yml", Collections.emptyMap(), Collections.emptyMap());
            Assert.assertTrue(outputs.containsKey("MY_OUTPUT"));
            Assert.assertEquals("Great output", outputs.get("MY_OUTPUT"));

            playbookExecutor.executeArtifact("test", "test", "artifact-playbook.yml", Collections.emptyMap(), ImmutableMap.<String, String>builder().put("MY_CONF", "conf/conf.properties").build());
        }));
    }
}
