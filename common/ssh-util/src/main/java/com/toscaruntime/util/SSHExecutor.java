package com.toscaruntime.util;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An utility to execute scripts commands by SSH on remote server
 *
 * @author Minh Khang VU
 */
public class SSHExecutor implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(SSHUtil.class);

    private String user;
    private String ip;
    private int port;
    private String pemPath;

    private SshClient sshClient;

    private ClientSession clientSession;

    public SSHExecutor(String user, String ip, int port, String pemPath) {
        this.user = user;
        this.ip = ip;
        this.port = port;
        this.pemPath = pemPath;
    }

    public void init() throws Exception {
        this.sshClient = SshClient.setUpDefaultClient();
        this.sshClient.start();
        this.clientSession = SSHUtil.connect(this.sshClient, user, pemPath, ip, port);
        log.info("Session connected session for " + user + "@" + ip);
    }

    private void checkConnection() throws IOException, InterruptedException {
        if (clientSession.isClosed() || clientSession.isClosing()) {
            log.info("Reconnecting the session for " + user + "@" + ip);
            this.clientSession.close(false).await();
            this.clientSession = SSHUtil.connect(this.sshClient, user, pemPath, ip, port);
            log.info("Session for " + user + "@" + ip);
        }
    }

    @Override
    public void close() throws IOException {
        this.clientSession.close(false).await();
        this.sshClient.close(false).await();
        log.info("Session has been closed for " + user + "@" + ip);
    }

    public synchronized void executeCommand(String command, Map<String, String> env) throws Exception {
        // TODO bug seems to affect multiple channels on the same session
        log.info("Executing command " + command + " with environments " + env);
        checkConnection();
        SSHUtil.executeCommand(clientSession, command, env);
    }

    public synchronized void executeScript(String scriptPath, Map<String, String> env) throws Exception {
        // TODO bug seems to affect multiple channels on the same session
        log.info("Executing script " + scriptPath + " with environments " + env);
        checkConnection();
        SSHUtil.executeScript(clientSession, scriptPath, env);
    }
}
