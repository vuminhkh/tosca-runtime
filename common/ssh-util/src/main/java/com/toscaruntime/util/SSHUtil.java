package com.toscaruntime.util;

import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.Security;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.scp.ScpClient;
import org.apache.sshd.client.session.ClientSession;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SSHUtil {

    private static final Logger log = LoggerFactory.getLogger(SSHUtil.class);

    public static KeyPair loadKeyPair(String pemFile) {
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

    public static ClientSession connect(SshClient client, final String user, String pemPath, final String ip, final int port) throws IOException,
            InterruptedException {
        ClientSession session = client.connect(user, ip, port).await().getSession();
        KeyPair keyPair = SSHUtil.loadKeyPair(pemPath);
        if (keyPair != null) {
            session.addPublicKeyIdentity(keyPair);
        }
        session.auth().verify(5, TimeUnit.MINUTES);
        return session;
    }

    public static void executeCommand(String user, String ip, int port, String pemPath, final String command, final Map<String, String> env)
            throws IOException, InterruptedException {
        runShellActionWithoutSession(user, ip, port, pemPath, new ExecuteCommandWithShellAction(command), env);
    }

    public static void executeCommand(final ClientSession session, final String command, final Map<String, String> env) throws IOException {
        runShellActionWithSession(session, new ExecuteCommandWithShellAction(command), env);
    }

    public static void executeScript(String user, String ip, int port, String pemPath, final String scriptPath, final Map<String, String> env) throws IOException, InterruptedException {
        runShellActionWithoutSession(user, ip, port, pemPath, new ExecuteScriptWithShellAction(scriptPath), env);
    }

    public static void executeScript(ClientSession clientSession, final String scriptPath, final Map<String, String> env) throws IOException, InterruptedException {
        runShellActionWithSession(clientSession, new ExecuteScriptWithShellAction(scriptPath), env);
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
            session = connect(client, user, pemPath, ip, port);
            doWithSshAction.doSshAction(session);
        } finally {
            if (session != null) {
                session.close(false).await();
            }
            client.stop();
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
                        newLines = new String[]{currentLine.toString()};
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
                doExecuteCommand(commandWriter, "export " + envEntry.getKey() + "=" + envEntry.getValue());
            }
        }
    }

    private static class ExecuteScriptWithShellAction implements DoWithShellAction {

        private Path scriptPath;

        public ExecuteScriptWithShellAction(String scriptPath) {
            this.scriptPath = Paths.get(scriptPath);
        }

        @Override
        public String getCommandName() {
            return scriptPath.getFileName().toString();
        }

        @Override
        public void doWithShell(ClientSession session, PrintWriter commandWriter) throws IOException {
            String remoteDirectory = "/tmp";
            String remoteFile = remoteDirectory + "/" + UUID.randomUUID().toString() + scriptPath.getFileName();
            session.createScpClient().upload(scriptPath, remoteFile);
            doExecuteCommand(commandWriter, "chmod +x " + remoteFile);
            doExecuteCommand(commandWriter, remoteFile);
        }
    }

    private static class ExecuteCommandWithShellAction implements DoWithShellAction {

        private String command;

        public ExecuteCommandWithShellAction(String command) {
            this.command = command;
        }

        @Override
        public String getCommandName() {
            return command;
        }

        @Override
        public void doWithShell(ClientSession session, PrintWriter commandWriter) throws IOException {
            doExecuteCommand(commandWriter, command);
        }
    }

    private static void doExecuteCommand(PrintWriter commandWriter, String command) {
        commandWriter.println(command);
        commandWriter.flush();
    }

    private static void runShellActionWithoutSession(String user, String ip, final int port, String pemPath, final DoWithShellAction doWithShellAction, final Map<String, String> env)
            throws IOException,
            InterruptedException {
        doWithSshSession(user, ip, port, pemPath, new DoWithSshSessionAction() {
            @Override
            public void doSshAction(ClientSession session) throws IOException {
                runShellActionWithSession(session, doWithShellAction, env);
            }
        });
    }

    private static void runShellActionWithSession(ClientSession session, final DoWithShellAction doWithShellAction, final Map<String, String> env) throws IOException {
        ChannelShell channel = session.createShellChannel();
        try {
            PipedOutputStream pipedIn = new PipedOutputStream();
            PipedInputStream channelInStream = new PipedInputStream(pipedIn);
            PrintWriter commandWriter = new PrintWriter(pipedIn);
            channel.setUsePty(false);
            channel.setIn(channelInStream);
            channel.setOut(new ScriptOutputLogger(doWithShellAction.getCommandName(), false));
            channel.setErr(new ScriptOutputLogger(doWithShellAction.getCommandName(), true));
            channel.open().await();
            setEnv(commandWriter, env);
            doWithShellAction.doWithShell(session, commandWriter);
            doExecuteCommand(commandWriter, "exit");
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
            channel.close(false).await();
        }
    }
}
