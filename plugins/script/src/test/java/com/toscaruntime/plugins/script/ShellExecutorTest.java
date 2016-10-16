package com.toscaruntime.plugins.script;

import com.google.common.collect.ImmutableMap;
import com.toscaruntime.artifact.Executor;
import com.toscaruntime.exception.deployment.artifact.ArtifactExecutionException;
import com.toscaruntime.plugins.script.shell.ShellExecutor;
import com.toscaruntime.util.ClassLoaderUtil;
import com.toscaruntime.util.DockerConnection;
import com.toscaruntime.util.DockerTestUtilities;
import com.toscaruntime.util.SSHConnection;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collections;
import java.util.Map;

@RunWith(JUnit4.class)
public class ShellExecutorTest {

    @Test
    public void testDocker() throws Exception {
        DockerTestUtilities.testWithBasicContainer(containerProperties -> {
            DockerConnection dockerConnection = new DockerConnection();
            dockerConnection.initialize(containerProperties);
            ShellExecutor shellExecutor = new ShellExecutor();
            shellExecutor.initialize(dockerConnection, ImmutableMap.<String, Object>builder()
                    .put(Executor.LOCAL_RECIPE_LOCATION_KEY, ClassLoaderUtil.getPathForResource("scripts").toString())
                    .put(Executor.RECIPE_LOCATION_KEY, "/tmp/recipe")
                    .build()
            );
            shellExecutor.executeArtifact("testNode", "testOperation", "good-script.sh", ImmutableMap.<String, Object>builder().put("MY_VARIABLE", "Good variable").build(), Collections.emptyMap());
            try {
                shellExecutor.executeArtifact("testNode", "testOperation", "bad-script.sh", ImmutableMap.<String, Object>builder().put("MY_VARIABLE", "Bad variable").build(), Collections.emptyMap());
                Assert.fail("This script must fail");
            } catch (ArtifactExecutionException ignored) {
            }
            Map<String, Object> outputs = shellExecutor.executeArtifact("testNode", "testOperation", "output-script.sh", ImmutableMap.<String, Object>builder().put("MY_VARIABLE", "Good variable").build(), Collections.emptyMap());
            Assert.assertNotNull(outputs);
            Assert.assertEquals("Good variable", outputs.get("MY_OUTPUT"));

            shellExecutor.executeArtifact("testNode", "testOperation", "artifact-script.sh", Collections.emptyMap(), ImmutableMap.<String, String>builder().put("MY_CONF", "conf.properties").build());
        });
    }

    @Test
    public void testSSH() throws Exception {
        DockerTestUtilities.testWithSSHEnabledContainer(containerProperties -> {
            SSHConnection sshConnection = new SSHConnection();
            sshConnection.initialize(containerProperties);
            ShellExecutor shellExecutor = new ShellExecutor();
            shellExecutor.initialize(sshConnection, ImmutableMap.<String, Object>builder()
                    .put(Executor.LOCAL_RECIPE_LOCATION_KEY, ClassLoaderUtil.getPathForResource("scripts").toString())
                    .put(Executor.RECIPE_LOCATION_KEY, "/tmp/recipe")
                    .build()
            );
            shellExecutor.executeArtifact("testNode", "testOperation", "good-script.sh", ImmutableMap.<String, Object>builder().put("MY_VARIABLE", "Good variable").build(), Collections.emptyMap());
            try {
                shellExecutor.executeArtifact("testNode", "testOperation", "bad-script.sh", ImmutableMap.<String, Object>builder().put("MY_VARIABLE", "Bad variable").build(), Collections.emptyMap());
                Assert.fail("This script must fail");
            } catch (ArtifactExecutionException ignored) {
            }
            Map<String, Object> outputs = shellExecutor.executeArtifact("testNode", "testOperation", "output-script.sh", ImmutableMap.<String, Object>builder().put("MY_VARIABLE", "Good variable").build(), Collections.emptyMap());
            Assert.assertNotNull(outputs);
            Assert.assertEquals("Good variable", outputs.get("MY_OUTPUT"));

            shellExecutor.executeArtifact("testNode", "testOperation", "artifact-script.sh", Collections.emptyMap(), ImmutableMap.<String, String>builder().put("MY_CONF", "conf.properties").build());
        });
    }
}
