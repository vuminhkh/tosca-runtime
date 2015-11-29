package com.toscaruntime.util;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

    private static final SshClient SSH_CLIENT = SshClient.setUpDefaultClient();

    static {
        SSH_CLIENT.start();
    }

    private static final Logger log = LoggerFactory.getLogger(SSHUtil.class);

    private static final long TIME_OUT = 5L;

    private static final TimeUnit TIME_UNIT = TimeUnit.MINUTES;

    private String user;
    private String ip;
    private int port;
    private String pemPath;

    private ClientSession clientSession;

    public SSHExecutor(String user, String ip, int port, String pemPath) {
        this.user = user;
        this.ip = ip;
        this.port = port;
        this.pemPath = pemPath;
    }

    public void init() throws Exception {
        log.info("Session is going to be connected for " + user + "@" + ip);
        this.clientSession = SSHUtil.connect(SSH_CLIENT, user, pemPath, ip, port, TIME_OUT, TIME_UNIT);
        log.info("Session is connected session for " + user + "@" + ip);
    }

    private void checkConnection() throws IOException, InterruptedException {
        if (clientSession == null) {
            log.info("Recreating the session for " + user + "@" + ip);
            this.clientSession = SSHUtil.connect(SSH_CLIENT, user, pemPath, ip, port, TIME_OUT, TIME_UNIT);
            log.info("Recreated the session for " + user + "@" + ip);
        } else if (clientSession.isClosed() || clientSession.isClosing()) {
            log.info("Reconnecting the session for " + user + "@" + ip);
            this.clientSession.close(false).await(TIME_OUT, TIME_UNIT);
            this.clientSession = SSHUtil.connect(SSH_CLIENT, user, pemPath, ip, port, TIME_OUT, TIME_UNIT);
            log.info("Reconnected the session for " + user + "@" + ip);
        }
    }

    @Override
    public void close() throws IOException {
        this.clientSession.close(false).await(TIME_OUT, TIME_UNIT);
        log.info("Session has been closed for " + user + "@" + ip);
    }

    public synchronized void executeCommand(String command, Map<String, String> env) throws Exception {
        // TODO bug seems to affect multiple channels on the same session
        log.info("Executing command " + command + " with environments " + env);
        checkConnection();
        SSHUtil.executeCommand(clientSession, command, env, TIME_OUT, TIME_UNIT);
    }

    public synchronized void executeScript(String scriptPath, Map<String, String> env) throws Exception {
        // TODO bug seems to affect multiple channels on the same session
        log.info("Executing script " + scriptPath + " with environments " + env);
        checkConnection();
        SSHUtil.executeScript(clientSession, scriptPath, env, TIME_OUT, TIME_UNIT);
    }
}
