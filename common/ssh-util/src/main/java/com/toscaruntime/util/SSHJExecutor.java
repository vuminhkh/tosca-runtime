package com.toscaruntime.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Security;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.toscaruntime.artifact.ArtifactExecutor;
import com.toscaruntime.artifact.ArtifactUploader;
import com.toscaruntime.exception.ArtifactAuthenticationFailureException;
import com.toscaruntime.exception.ArtifactConnectException;
import com.toscaruntime.exception.ArtifactException;
import com.toscaruntime.exception.ArtifactExecutionException;
import com.toscaruntime.exception.ArtifactInterruptedException;
import com.toscaruntime.exception.ArtifactUploadException;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.common.SecurityUtils;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.SessionChannel;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.userauth.UserAuthException;
import net.schmizz.sshj.xfer.scp.SCPFileTransfer;

public class SSHJExecutor implements Closeable, ArtifactExecutor, ArtifactUploader {

    private static final Logger log = LoggerFactory.getLogger(SSHJExecutor.class);

    static {
        Security.removeProvider(SecurityUtils.BOUNCY_CASTLE);
        SecurityUtils.setRegisterBouncyCastle(true);
        if (SecurityUtils.isBouncyCastleRegistered()) {
            log.info("Bouncy Castle registered");
        } else {
            log.warn("Bouncy Castle not registered");
        }
    }

    private ExecutorService executorService = Executors.newCachedThreadPool(new ThreadFactory() {

        private AtomicInteger count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("SSHJ_Thread_" + count.incrementAndGet());
            return t;
        }
    });

    private final SSHClient sshClient = new SSHClient();

    private String user;

    private String ip;

    private int port;

    private String pemPath;

    public SSHJExecutor(String user, String ip, int port, String pemPath) {
        this.user = user;
        this.ip = ip;
        this.port = port;
        this.pemPath = pemPath;
        this.sshClient.addHostKeyVerifier((h, p, k) -> true);
    }

    @Override
    public void initialize() throws ArtifactException {
        // Trust every host
        try {
            sshClient.connect(ip, port);
            sshClient.authPublickey(user, pemPath);
        } catch (UserAuthException e) {
            throw new ArtifactAuthenticationFailureException("Authentication failure, must be bad key file", e);
        } catch (IOException e) {
            throw new ArtifactConnectException("Cannot connect to the host", e);
        }
    }

    private synchronized void checkConnection() throws ArtifactException {
        if (!sshClient.isConnected() || !sshClient.isAuthenticated()) {
            log.info("Reconnecting the session for " + user + "@" + ip);
            initialize();
            log.info("Reconnected the session for " + user + "@" + ip);
        }
    }

    @Override
    public Map<String, String> executeArtifact(String operationName, String artifactPath, Map<String, String> env) throws ArtifactException {
        checkConnection();
        String artifactName = Paths.get(artifactPath).getFileName().toString();
        try (Session session = sshClient.startSession();
             SessionChannel shell = (SessionChannel) session.startShell();
             PrintWriter commandWriter = new PrintWriter(shell.getOutputStream())) {
            String endOfOutputToken = UUID.randomUUID().toString();
            SSHJStdOutLogger stdOutLogger = new SSHJStdOutLogger(operationName, artifactName, log, endOfOutputToken, shell.getInputStream());
            Future<?> endStdOut = executorService.submit(stdOutLogger);
            Future<?> endStdErr = executorService.submit(new SSHJStdErrLogger(operationName, artifactName, log, shell.getErrorStream()));
            // Set envs
            if (env != null) {
                env.entrySet().stream().filter(envEntry -> envEntry.getValue() != null).forEach(
                        envEntry -> commandWriter.println("export " + envEntry.getKey() + "='" + envEntry.getValue() + "'")
                );
            }
            // Make executable
            commandWriter.println("chmod +x " + artifactPath);
            commandWriter.println(". " + artifactPath);
            commandWriter.println("_toscaruntime_rc=$?; if [[ $_toscaruntime_rc != 0 ]]; then echo \"Script exit with status $_toscaruntime_rc\"; exit $_toscaruntime_rc; fi");
            commandWriter.println("echo " + endOfOutputToken);
            commandWriter.println("printenv");
            commandWriter.println("exit");
            commandWriter.flush();
            session.join();
            endStdOut.get();
            endStdErr.get();
            if (shell.getExitStatus() != 0) {
                throw new ArtifactExecutionException("[" + operationName + "][" + artifactPath + "] failed to executed with exit status " + shell.getExitStatus());
            } else {
                log.info("[{}][{}] execution finished normally", operationName, artifactName);
            }
            return stdOutLogger.getCapturedEnvVars();
        } catch (ConnectionException | TransportException e) {
            throw new ArtifactConnectException("[" + operationName + "][" + artifactPath + "] execution connection error", e);
        } catch (ExecutionException e) {
            throw new ArtifactExecutionException("[" + operationName + "][" + artifactPath + "] Error happened while trying to read script's output", e);
        } catch (InterruptedException e) {
            throw new ArtifactInterruptedException("[" + operationName + "][" + artifactPath + "] Interrupted", e);
        }
    }

    private void runCommand(Session session, String command) {
        try (Session.Command sshCommand = session.exec(command)) {
            sshCommand.join();
            if (sshCommand.getExitStatus() != 0) {
                throw new ArtifactExecutionException("Command " + command + " failed with exit status " + sshCommand.getExitStatus());
            } else {
                log.info("Command [{}] finished normally with standard output [{}] and error output [{}]",
                        command,
                        new String(IOUtils.readFully(sshCommand.getInputStream()).toByteArray()),
                        new String(IOUtils.readFully(sshCommand.getErrorStream()).toByteArray()));
            }
        } catch (IOException e) {
            throw new ArtifactExecutionException("Command " + command + " has failed", e);
        }
    }

    @Override
    public void upload(String localPath, String remotePath) throws ArtifactException {
        checkConnection();
        try {
            Path parentPath = Paths.get(remotePath).getParent();
            String prepareCommand;
            if (parentPath != null) {
                // Delete if already exists and create the parent if given remote path has parent
                prepareCommand = "if [ -e \"" + remotePath + "\" ]; then echo \"Remote path " + remotePath + "already exist, will overwrite\"; rm -rf \"" + remotePath + "\"; else mkdir -p \"" + parentPath.toString() + "\"; fi";
            } else {
                // Delete if already exists
                prepareCommand = "if [ -e \"" + remotePath + "\" ]; then echo \"Remote path [" + remotePath + "] already exist, will overwrite\"; rm -rf \"" + remotePath + "\"; fi";
            }
            try (Session session = sshClient.startSession()) {
                runCommand(session, prepareCommand);
            }
            SCPFileTransfer scpFileTransfer = sshClient.newSCPFileTransfer();
            scpFileTransfer.upload(localPath, remotePath);
        } catch (ConnectionException | TransportException e) {
            throw new ArtifactConnectException("Upload [" + localPath + "] to [" + remotePath + "] encountered connection error", e);
        } catch (IOException e) {
            throw new ArtifactUploadException("Fatal error happened while trying to upload", e);
        }
    }

    @Override
    public void close() {
        try {
            sshClient.close();
        } catch (IOException e) {
            log.warn("Could not close ssh executor", e);
        }
    }
}
