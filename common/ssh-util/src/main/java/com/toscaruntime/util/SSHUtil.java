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
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.RuntimeSshException;
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

    public static ClientSession connect(SshClient client, final String user, String pemPath, final String ip, final int port, long timeout, TimeUnit timeUnit) throws IOException,
            InterruptedException {
        ConnectFuture connectFuture = client.connect(user, ip, port);
        boolean connected = connectFuture.await(timeout, timeUnit);
        if (!connected) {
            throw new RuntimeSshException("Connection timed out");
        }
        ClientSession session = connectFuture.getSession();
        KeyPair keyPair = SSHUtil.loadKeyPair(pemPath);
        if (keyPair != null) {
            session.addPublicKeyIdentity(keyPair);
        }
        session.auth().verify(timeout, timeUnit);
        return session;
    }

    public static void executeCommand(final ClientSession session, final String command, final Map<String, String> env, long timeout, TimeUnit timeUnit) throws IOException {
        runShellActionWithSession(session, new ExecuteCommandWithShellAction(command), env, timeout, timeUnit);
    }

    public static void executeScript(ClientSession clientSession, final String scriptPath, final Map<String, String> env, long timeout, TimeUnit timeUnit) throws IOException, InterruptedException {
        runShellActionWithSession(clientSession, new ExecuteScriptWithShellAction(scriptPath), env, timeout, timeUnit);
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
                    String logLine = "[" + scriptName + "][" + (isError ? "stderr" : "stdout") + "] " + newLine;
                    log.info(logLine);
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

    private static void runShellActionWithSession(ClientSession session, final DoWithShellAction doWithShellAction, final Map<String, String> env, final long timeout, final TimeUnit timeUnit) throws IOException {
        ChannelShell channel = session.createShellChannel();
        try {
            PipedOutputStream pipedIn = new PipedOutputStream();
            PipedInputStream channelInStream = new PipedInputStream(pipedIn);
            PrintWriter commandWriter = new PrintWriter(pipedIn);
            channel.setUsePty(false);
            channel.setIn(channelInStream);
            channel.setOut(new ScriptOutputLogger(doWithShellAction.getCommandName(), false));
            channel.setErr(new ScriptOutputLogger(doWithShellAction.getCommandName(), true));
            log.info("[{}] Channel is going to be opened", doWithShellAction.getCommandName());
            boolean channelOpened = channel.open().await(timeout, timeUnit);
            if (!channelOpened) {
                throw new RuntimeSshException("Timed out when trying to open channel");
            }
            log.info("[{}] Channel opened", doWithShellAction.getCommandName());
            setEnv(commandWriter, env);
            doWithShellAction.doWithShell(session, commandWriter);
            doExecuteCommand(commandWriter, "exit");
            log.info("[{}] Waiting for channel closed", doWithShellAction.getCommandName());
            channel.waitFor(ClientChannel.CLOSED, timeUnit.toMillis(timeout));
            log.info("[{}] Received channel closed", doWithShellAction.getCommandName());
            Integer exitStatus = channel.getExitStatus();
            if (exitStatus != null && exitStatus == 0) {
                log.info("[{}] exited normally", doWithShellAction.getCommandName());
            } else {
                log.error("[{}] exited with error status {}", doWithShellAction.getCommandName(), exitStatus);
                throw new RuntimeSshException("[" + doWithShellAction.getCommandName() + "] exited with error status " + exitStatus);
            }
        } catch (IOException e) {
            log.error("Error while executing on channel", e);
            throw e;
        } finally {
            channel.close(false).await(timeout, timeUnit);
            log.info("[{}] Channel closed", doWithShellAction.getCommandName());
        }
    }
}
