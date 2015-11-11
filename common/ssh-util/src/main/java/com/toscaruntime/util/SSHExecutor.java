package com.toscaruntime.util;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;

/**
 * An utility to execute scripts commands by SSH on remote server
 *
 * @author Minh Khang VU
 */
public class SSHExecutor implements Closeable {

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
        this.clientSession = SSHUtil.connect(this.sshClient, user, SSHUtil.loadKeyPair(pemPath), ip, port);
    }

    @Override
    public void close() throws IOException {
        this.clientSession.close();
        this.sshClient.stop();
    }

    public void executeCommand(String command, Map<String, String> env) throws Exception {
        SSHUtil.executeCommand(clientSession, command, env);
    }

    public void executeScript(String scriptPath, Map<String, String> env) throws Exception {
        SSHUtil.executeScript(clientSession, scriptPath, env);
    }
}
