package com.toscaruntime.util;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.scp.ScpClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.RuntimeSshException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SSHUtil {

    private static final Logger log = LoggerFactory.getLogger(SSHUtil.class);

    public static final String PRINT_ENV_COMMAND = "printenv";

    public static final String EXIT_COMMAND = "exit";

    public static ClientSession connect(SshClient client, final String user, String pemPath, final String ip, final int port, long timeout, TimeUnit timeUnit) throws IOException,
            InterruptedException {
        ConnectFuture connectFuture = client.connect(user, ip, port);
        boolean connected = connectFuture.await(timeout, timeUnit);
        if (!connected) {
            throw new RuntimeSshException("Connection timed out");
        }
        ClientSession session = connectFuture.getSession();
        KeyPair keyPair = KeyPairUtil.loadKeyPair(pemPath);
        if (keyPair != null) {
            session.addPublicKeyIdentity(keyPair);
        }
        session.auth().verify(timeout, timeUnit);
        return session;
    }

    /**
     * Run a command and return the standard output of the session
     *
     * @param operationName Name of the operation
     * @param session       the SSH session
     * @param command       the command to execute
     * @param env           the environment variables
     * @param timeout       the timeout for each wait action on the session
     * @param timeUnit      unit of the timeout
     * @return all captured environment variables produced by printenv after execution of the command
     * @throws IOException
     */
    public static Map<String, String> executeCommand(String operationName, final ClientSession session, final String command, final Map<String, String> env, long timeout, TimeUnit timeUnit) throws IOException {
        return runShellActionWithSession(operationName, session, new ExecuteCommandWithShellAction(command), env, timeout, timeUnit);
    }

    /**
     * Run a script and return the standard output of the session
     *
     * @param operationName    Name of the operation
     * @param session          the SSH session
     * @param remoteScriptPath the path to the script to execute at the remote machinve
     * @param env              the environment variables
     * @param timeout          the timeout for each wait action on the session
     * @param timeUnit         unit of the timeout
     * @return all captured environment variables produced by printenv after execution of the command
     * @throws IOException
     */
    public static Map<String, String> executeScript(String operationName, ClientSession session, final String remoteScriptPath, final Map<String, String> env, long timeout, TimeUnit timeUnit) throws IOException, InterruptedException {
        return runShellActionWithSession(operationName, session, new ExecuteScriptWithShellAction(remoteScriptPath), env, timeout, timeUnit);
    }

    /**
     * Upload a directory or a file to the remote path. If local path is a directory, all of its children will be uploaded to the remote directory.
     * For ex: local path /toto has children /toto/tata, /toto/titi and remote path is /somePath/that/does/not/exist in this case, on the remote machine,
     * those files will be uploaded as /somePath/that/does/not/exist/tata and /somePath/that/does/not/exist/titi.
     * If remote path is /somePath/that/exists then the files will be uploaded at /somePath/that/exists/toto/tata /somePath/that/exists/toto/titi
     *
     * @param session    the SSH session
     * @param localPath  the local path
     * @param remotePath the remote path
     * @throws IOException
     */
    public static void upload(ClientSession session, String localPath, String remotePath, long timeout, TimeUnit timeUnit) throws IOException {
        ScpClient scpClient = session.createScpClient();
        Path nioPath = Paths.get(localPath);
        if (!Files.isReadable(nioPath)) {
            throw new IOException("File is not readable " + localPath);
        }
        Path nioRemotePath = Paths.get(remotePath);
        ChannelExec channelExec = session.createExecChannel("mkdir -p " + nioRemotePath.getParent());
        if (!channelExec.open().await(timeout, timeUnit)) {
            throw new IOException("Could not open channel to prepare remote path " + remotePath);
        }
        channelExec.waitFor(EnumSet.of(ClientChannel.ClientChannelEvent.CLOSED), timeUnit.toMillis(timeout));
        // It's not really big deal if we do not wait until it's really closed
        channelExec.close(false).await(5, TimeUnit.SECONDS);
        if (Files.isDirectory(nioPath)) {
            scpClient.upload(nioPath, remotePath, ScpClient.Option.Recursive);
        } else {
            scpClient.upload(nioPath, remotePath);
        }
    }

    private interface DoWithShellAction {

        String getCommandName();

        void doWithShell(ClientSession session, PrintWriter commandWriter) throws IOException;
    }

    private static void setEnv(PrintWriter commandWriter, Map<String, String> env) {
        if (env != null) {
            env.entrySet().stream().filter(envEntry -> envEntry.getValue() != null).forEach(
                    envEntry -> doExecuteCommand(commandWriter, "export " + envEntry.getKey() + "='" + envEntry.getValue() + "'")
            );
        }
    }

    private static class ExecuteScriptWithShellAction implements DoWithShellAction {

        private String scriptPath;

        private String name;

        public ExecuteScriptWithShellAction(String scriptPath) {
            this.scriptPath = scriptPath;
            this.name = Paths.get(scriptPath).getFileName().toString();
        }

        @Override
        public String getCommandName() {
            return this.name;
        }

        @Override
        public void doWithShell(ClientSession session, PrintWriter commandWriter) throws IOException {
            doExecuteCommand(commandWriter, "chmod +x " + scriptPath);
            doExecuteCommand(commandWriter, ". " + scriptPath);
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

    private static Map<String, String> runShellActionWithSession(String operationName, ClientSession session, final DoWithShellAction doWithShellAction, final Map<String, String> env, final long timeout, final TimeUnit timeUnit) throws IOException {
        ChannelShell channel = session.createShellChannel();
        try {
            PipedOutputStream pipedIn = new PipedOutputStream();
            PipedInputStream channelInStream = new PipedInputStream(pipedIn);
            PrintWriter commandWriter = new PrintWriter(pipedIn);
            channel.setUsePty(false);
            channel.setIn(channelInStream);
            String endOfOutputToken = UUID.randomUUID().toString();
            SSHStdOutLoggerOutputStream loggerOutputStream = new SSHStdOutLoggerOutputStream(operationName, doWithShellAction.getCommandName(), log, endOfOutputToken);
            channel.setOut(loggerOutputStream);
            channel.setErr(new SSHStdErrLoggerOutputStream(operationName, doWithShellAction.getCommandName(), log));
            log.info("[{}][{}] Channel is going to be opened", operationName, doWithShellAction.getCommandName());
            boolean channelOpened = channel.open().await(timeout, timeUnit);
            if (!channelOpened) {
                throw new RuntimeSshException("Timed out when trying to open channel");
            }
            log.info("[{}][{}] Channel opened", operationName, doWithShellAction.getCommandName());
            setEnv(commandWriter, env);
            doWithShellAction.doWithShell(session, commandWriter);
            doExecuteCommand(commandWriter, "echo " + endOfOutputToken);
            doExecuteCommand(commandWriter, PRINT_ENV_COMMAND);
            doExecuteCommand(commandWriter, EXIT_COMMAND);
            log.info("[{}][{}] Waiting for channel closed", operationName, doWithShellAction.getCommandName());
            channel.waitFor(EnumSet.of(ClientChannel.ClientChannelEvent.CLOSED), timeUnit.toMillis(timeout));
            log.info("[{}][{}] Received channel closed", operationName, doWithShellAction.getCommandName());
            Integer exitStatus = channel.getExitStatus();
            if (exitStatus != null && exitStatus == 0) {
                log.info("[{}][{}] exited normally", operationName, doWithShellAction.getCommandName());
            } else {
                log.error("[{}][{}] exited with error status {}", operationName, doWithShellAction.getCommandName(), exitStatus);
                throw new RuntimeSshException("[" + doWithShellAction.getCommandName() + "] exited with error status " + exitStatus);
            }
            return loggerOutputStream.getCapturedEnvVars();
        } catch (IOException e) {
            log.error("Error while executing on channel", e);
            throw e;
        } finally {
            boolean couldCloseInTime = channel.close(false).await(5, TimeUnit.SECONDS);
            if (couldCloseInTime) {
                log.info("[{}][{}] Channel closed", operationName, doWithShellAction.getCommandName());
            } else {
                log.info("[{}][{}] Channel could not be closed correctly", operationName, doWithShellAction.getCommandName());
            }
        }
    }
}
