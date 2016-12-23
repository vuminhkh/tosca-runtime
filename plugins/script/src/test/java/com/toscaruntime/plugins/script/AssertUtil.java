package com.toscaruntime.plugins.script;

import com.google.common.collect.ImmutableMap;
import com.toscaruntime.artifact.Connection;
import com.toscaruntime.artifact.Executor;
import com.toscaruntime.artifact.SimpleCommandOutputHandler;
import com.toscaruntime.exception.deployment.artifact.ArtifactExecutionException;
import com.toscaruntime.util.ClassLoaderUtil;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.Assert;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

public class AssertUtil {

    public static void verifyConnectionExecute(Connection connection, Logger log) throws IOException {
        Assert.assertEquals(0, (int) connection.executeArtifact(new String(IOUtils.toByteArray(AssertUtil.class.getClassLoader()
                .getResourceAsStream("scripts/good-script.sh")), StandardCharsets.UTF_8), ImmutableMap.<String, Object>builder().put("MY_VARIABLE", "Good variable").build(), new SimpleCommandOutputHandler("good-script.sh", log)));
        Assert.assertNotEquals(0, (int) connection.executeArtifact(new String(IOUtils.toByteArray(AssertUtil.class.getClassLoader()
                .getResourceAsStream("scripts/bad-script.sh"))), ImmutableMap.<String, Object>builder().put("MY_VARIABLE", "Bad variable").build(), new SimpleCommandOutputHandler("bad-script.sh", log)));
        Assert.assertEquals(0, (int) connection.executeCommand("echo \"I'm  a good command\""));
    }

    public static void verifyConnectionUpload(Connection connection) {
        Assert.assertNotEquals(0, (int) connection.executeCommand("cd /tmp/scripts"));
        connection.upload(ClassLoaderUtil.getPathForResource("scripts").toString(), "/tmp/scripts");
        Assert.assertEquals(0, (int) connection.executeCommand("cd /tmp/scripts"));
    }

    public static void verifyConnection(Connection connection, Logger log) throws IOException {
        verifyConnectionExecute(connection, log);
        verifyConnectionUpload(connection);
    }

    public static void verifyExecutor(Executor executor) {
        executor.executeArtifact("testNode", "testOperation", "good-script.sh", ImmutableMap.<String, Object>builder().put("MY_VARIABLE", "Good variable").build(), Collections.emptyMap());
        try {
            executor.executeArtifact("testNode", "testOperation", "bad-script.sh", ImmutableMap.<String, Object>builder().put("MY_VARIABLE", "Bad variable").build(), Collections.emptyMap());
            Assert.fail("This script must fail");
        } catch (ArtifactExecutionException ignored) {
        }
        Map<String, Object> outputs = executor.executeArtifact("testNode", "testOperation", "output-script.sh", ImmutableMap.<String, Object>builder().put("MY_VARIABLE", "Good variable").build(), Collections.emptyMap());
        Assert.assertNotNull(outputs);
        Assert.assertEquals("Good variable", outputs.get("MY_OUTPUT"));
        executor.executeArtifact("testNode", "testOperation", "artifact-script.sh", Collections.emptyMap(), ImmutableMap.<String, String>builder().put("MY_CONF", "conf.properties").build());
    }
}
