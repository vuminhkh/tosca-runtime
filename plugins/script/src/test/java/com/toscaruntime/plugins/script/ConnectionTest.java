package com.toscaruntime.plugins.script;

import com.toscaruntime.connection.DockerConnection;
import com.toscaruntime.connection.DockerTestUtilities;
import com.toscaruntime.connection.LocalConnection;
import com.toscaruntime.connection.SSHConnection;
import net.schmizz.sshj.common.SecurityUtils;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Security;
import java.util.Collections;

public class ConnectionTest {

    private static final Logger log = LoggerFactory.getLogger(ConnectionTest.class);

    static {
        Security.removeProvider(SecurityUtils.BOUNCY_CASTLE);
        SecurityUtils.setRegisterBouncyCastle(true);
        if (SecurityUtils.isBouncyCastleRegistered()) {
            log.info("Bouncy Castle registered");
        } else {
            log.warn("Bouncy Castle not registered");
        }
    }

    @Test
    public void testDockerConnection() throws Exception {
        DockerTestUtilities.testWithBasicContainer(containerProperties -> {
            DockerConnection dockerConnection = new DockerConnection();
            dockerConnection.initialize(containerProperties);
            AssertUtil.verifyConnection(dockerConnection, log);
        });
    }

    @Test
    public void testSSHConnection() throws Exception {
        DockerTestUtilities.testWithSSHEnabledContainer(containerProperties -> {
            SSHConnection sshConnection = new SSHConnection();
            sshConnection.initialize(containerProperties);
            AssertUtil.verifyConnection(sshConnection, log);
        });
    }

    @Test
    public void testLocalConnection() throws Exception {
        LocalConnection localConnection = new LocalConnection();
        localConnection.initialize(Collections.emptyMap());
        AssertUtil.verifyConnectionExecute(localConnection, log);
        Path testFile = Files.createTempFile("testLocalConnection", "");
        Path movedTestFile = Files.createTempFile("movedTestLocalConnection", "");
        String moveCommand = "mv " + testFile + " " + movedTestFile + " && echo 'hello'";
        Assert.assertEquals(0, localConnection.executeCommand(moveCommand).intValue());
    }
}
