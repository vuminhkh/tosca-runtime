package com.toscaruntime.plugins.script;

import com.google.common.collect.ImmutableMap;
import com.toscaruntime.artifact.Connection;
import com.toscaruntime.artifact.Executor;
import com.toscaruntime.connection.DockerConnection;
import com.toscaruntime.connection.DockerTestUtilities;
import com.toscaruntime.connection.LocalConnection;
import com.toscaruntime.connection.SSHConnection;
import com.toscaruntime.plugins.script.shell.ShellExecutor;
import com.toscaruntime.util.ClassLoaderUtil;
import net.schmizz.sshj.common.SecurityUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Security;

@RunWith(JUnit4.class)
public class ShellExecutorTest {

    private static final Logger log = LoggerFactory.getLogger(ShellExecutorTest.class);

    static {
        Security.removeProvider(SecurityUtils.BOUNCY_CASTLE);
        SecurityUtils.setRegisterBouncyCastle(true);
        if (SecurityUtils.isBouncyCastleRegistered()) {
            log.info("Bouncy Castle registered");
        } else {
            log.warn("Bouncy Castle not registered");
        }
    }

    private static ShellExecutor createShellExecutor(Connection connection, String remoteRecipeLocation) {
        ShellExecutor shellExecutor = new ShellExecutor();
        shellExecutor.initialize(connection, ImmutableMap.<String, Object>builder()
                .put(Executor.LOCAL_RECIPE_LOCATION_KEY, ClassLoaderUtil.getPathForResource("scripts").toString())
                .put(Executor.RECIPE_LOCATION_KEY, remoteRecipeLocation)
                .build()
        );
        return shellExecutor;
    }

    private static ShellExecutor createShellExecutor(Connection connection) {
        return createShellExecutor(connection, "/tmp/recipe");
    }

    @Test
    public void testDocker() throws Exception {
        DockerTestUtilities.testWithBasicContainer(containerProperties -> {
            DockerConnection dockerConnection = new DockerConnection();
            dockerConnection.initialize(containerProperties);
            AssertUtil.verifyExecutor(createShellExecutor(dockerConnection));
        });
    }

    @Test
    public void testSSH() throws Exception {
        DockerTestUtilities.testWithSSHEnabledContainer(containerProperties -> {
            SSHConnection sshConnection = new SSHConnection();
            sshConnection.initialize(containerProperties);
            AssertUtil.verifyExecutor(createShellExecutor(sshConnection));
        });
    }

    @Test
    public void testLocal() throws Exception {
        LocalConnection localConnection = new LocalConnection();
        AssertUtil.verifyExecutor(createShellExecutor(localConnection, ClassLoaderUtil.getPathForResource("scripts").toString()));
    }
}
