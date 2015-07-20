package com.mkv.util;

import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.Security;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang.StringUtils;
import org.apache.sshd.ClientChannel;
import org.apache.sshd.ClientSession;
import org.apache.sshd.SshClient;
import org.apache.sshd.client.ScpClient;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.common.future.SshFutureListener;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SSHUtil {

    private static final Logger log = LoggerFactory.getLogger(SSHUtil.class);

    private static KeyPair loadKeyPair(String pemFile) {
        if (StringUtils.isBlank(pemFile)) {
            return null;
        }
        try {
            Security.addProvider(new BouncyCastleProvider());
            PEMParser pemParser = new PEMParser(new FileReader(pemFile));
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            Object object = pemParser.readObject();
            return converter.getKeyPair((PEMKeyPair) object);
        } catch (Exception e) {
            log.error("Could not load key pair", e);
            throw new RuntimeException("Could not load key pair", e);
        }
    }

    private static ClientSession connect(SshClient client, final String user, KeyPair keyPair, final String ip, final int port) throws IOException,
            InterruptedException {
        ClientSession session = client.connect(user, ip, port).await().getSession();
        int authState = ClientSession.WAIT_AUTH;
        while ((authState & ClientSession.WAIT_AUTH) != 0) {
            if (keyPair != null) {
                session.addPublicKeyIdentity(keyPair);
            }
            log.info("Authenticating to " + user + "@" + ip);
            AuthFuture authFuture = session.auth();
            authFuture.addListener(new SshFutureListener<AuthFuture>() {
                @Override
                public void operationComplete(AuthFuture authFuture) {
                    log.info("Authentication completed with " + (authFuture.isSuccess() ? "success" : "failure") + " for " + user + "@" + ip + ":" + port);
                }
            });
            authState = session.waitFor(ClientSession.WAIT_AUTH | ClientSession.CLOSED | ClientSession.AUTHED, 0);
        }

        if ((authState & ClientSession.CLOSED) != 0) {
            throw new IOException("Authentication failed for " + user + "@" + ip);
        }
        return session;
    }

    private interface DoWithSshSessionAction {
        void doSshAction(ClientSession session) throws IOException;
    }

    private static void doWithSshSession(String user, String ip, int port, String pemPath, DoWithSshSessionAction doWithSshAction) throws IOException,
            InterruptedException {
        SshClient client = SshClient.setUpDefaultClient();
        ClientSession session = null;
        try {
            client.start();
            session = connect(client, user, loadKeyPair(pemPath), ip, port);
            doWithSshAction.doSshAction(session);
        } finally {
            if (session != null) {
                session.close(false);
            }
            client.close(false);
        }
    }

    public static void download(String user, String ip, int port, String pemPath, final String remote, final String local) throws IOException,
            InterruptedException {
        doWithSshSession(user, ip, port, pemPath, new DoWithSshSessionAction() {
            @Override
            public void doSshAction(ClientSession session) throws IOException {
                session.createScpClient().download(remote, local, ScpClient.Option.Recursive);
            }
        });
    }

    public static void upload(String user, String ip, int port, String pemPath, final String remote, final String local) throws IOException,
            InterruptedException {
        doWithSshSession(user, ip, port, pemPath, new DoWithSshSessionAction() {
            @Override
            public void doSshAction(ClientSession session) throws IOException {
                session.createScpClient().upload(local, remote, ScpClient.Option.Recursive);
            }
        });
    }

    private static class ScriptOutputLogger extends OutputStream {

        private StringBuilder currentLine = new StringBuilder();

        private String scriptName;

        private boolean isError;

        public ScriptOutputLogger(String scriptName, boolean isError) {
            this.scriptName = scriptName;
            this.isError = isError;
        }

        @Override
        public void write(int b) throws IOException {
            byte[] d = new byte[1];
            d[0] = (byte) b;
            write(d, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            String newData = new String(b, off, len, "UTF-8");
            if (newData.contains("\n")) {
                String[] newLines = newData.split("\n");
                if (newLines.length == 0) {
                    if (currentLine.length() > 0) {
                        newLines = new String[] { currentLine.toString() };
                        currentLine.setLength(0);
                    }
                }
                if (currentLine.length() > 0) {
                    newLines[0] = currentLine.append(newLines[0]).toString();
                    currentLine.setLength(0);
                }
                for (String newLine : newLines) {
                    String logLine = "[" + scriptName + "]: " + newLine;
                    if (isError) {
                        log.error(logLine);
                    } else {
                        log.info(logLine);
                    }
                }
            } else {
                currentLine.append(newData);
            }
        }
    }

    private interface DoWithShellAction {

        String getCommandName();

        void doWithShell(ClientSession session, PrintWriter commandWriter) throws IOException;
    }

    private static void setEnv(PrintWriter commandWriter, Map<String, String> env) {
        if (env != null) {
            for (Map.Entry<String, String> envEntry : env.entrySet()) {
                commandWriter.write("export " + envEntry.getKey() + "=" + envEntry.getValue() + "\n");
            }
        }
    }

    public static void executeScript(String user, String ip, int port, String pemPath, final String scriptPath, final Map<String, String> env)
            throws IOException,
            InterruptedException {
        execute(user, ip, port, pemPath, new DoWithShellAction() {

            @Override
            public String getCommandName() {
                return Paths.get(scriptPath).getFileName().toString();
            }

            @Override
            public void doWithShell(ClientSession session, PrintWriter commandWriter) throws IOException {
                String remoteDirectory = "/tmp/" + UUID.randomUUID().toString();
                String remoteFile = remoteDirectory + "/" + Paths.get(scriptPath).getFileName();
                commandWriter.write("mkdir -p " + remoteDirectory + "\n");
                commandWriter.flush();
                session.createScpClient().upload(scriptPath, remoteDirectory, ScpClient.Option.TargetIsDirectory);
                commandWriter.write("chmod +x " + remoteFile + "\n");
                commandWriter.write(remoteFile + "\n");
            }
        }, env);
    }

    public static void executeCommand(String user, String ip, int port, String pemPath, final String command, final Map<String, String> env)
            throws IOException, InterruptedException {
        execute(user, ip, port, pemPath, new DoWithShellAction() {
            @Override
            public String getCommandName() {
                return command;
            }

            @Override
            public void doWithShell(ClientSession session, PrintWriter commandWriter) throws IOException {
                commandWriter.write(command + "\n");
                commandWriter.flush();
            }
        }, env);
    }

    private static void execute(String user, String ip, final int port, String pemPath, final DoWithShellAction doWithShellAction, final Map<String, String> env)
            throws IOException,
            InterruptedException {
        doWithSshSession(user, ip, port, pemPath, new DoWithSshSessionAction() {
            @Override
            public void doSshAction(ClientSession session) throws IOException {
                ChannelShell channel = session.createShellChannel();
                try {
                    PipedOutputStream pipedIn = new PipedOutputStream();
                    PipedInputStream channelInStream = new PipedInputStream(pipedIn);
                    channel.setUsePty(false);
                    channel.setIn(channelInStream);
                    channel.setOut(new ScriptOutputLogger(doWithShellAction.getCommandName(), false));
                    channel.setErr(new ScriptOutputLogger(doWithShellAction.getCommandName(), true));
                    PrintWriter commandWriter = new PrintWriter(pipedIn);
                    channel.open().await();
                    setEnv(commandWriter, env);
                    commandWriter.flush();
                    doWithShellAction.doWithShell(session, commandWriter);
                    commandWriter.write("exit\n");
                    commandWriter.flush();
                } catch (Exception e) {
                    log.error("Error while executing on channel", e);
                } finally {
                    while (channel.getExitStatus() == null) {
                        channel.waitFor(ClientChannel.CLOSED, 1000L);
                    }
                    Integer exitStatus = channel.getExitStatus();
                    if (exitStatus != null && exitStatus == 0) {
                        log.info("[{}] exited normally", doWithShellAction.getCommandName());
                    } else {
                        log.error("[{}] exited with error status {}", doWithShellAction.getCommandName(), exitStatus);
                    }
                }
            }
        });
    }
}
