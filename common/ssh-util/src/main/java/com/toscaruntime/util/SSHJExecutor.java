package com.toscaruntime.util;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.toscaruntime.artifact.ArtifactExecutor;
import com.toscaruntime.artifact.ArtifactUploader;
import com.toscaruntime.exception.deployment.artifact.ArtifactAuthenticationFailureException;
import com.toscaruntime.exception.deployment.artifact.ArtifactConnectException;
import com.toscaruntime.exception.deployment.artifact.ArtifactExecutionException;
import com.toscaruntime.exception.deployment.artifact.ArtifactUploadException;

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

    private static final String REMOTE_TEMP_DIR = "/tmp/";

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

    private SSHClient sshClient;

    private String user;

    private String ip;

    private int port;

    private String pemPath;

    private boolean elevatePrivilege;

    public SSHJExecutor(String user, String ip, int port, String pemPath, boolean elevatePrivilege) {
        this.user = user;
        this.ip = ip;
        this.port = port;
        this.pemPath = pemPath;
        this.elevatePrivilege = elevatePrivilege;
    }

    @Override
    public void initialize() {
        close();
        // Trust every host
        try {
            sshClient = new SSHClient();
            sshClient.addHostKeyVerifier((h, p, k) -> true);
            sshClient.connect(ip, port);
            sshClient.authPublickey(user, pemPath);
        } catch (UserAuthException e) {
            throw new ArtifactAuthenticationFailureException("Authentication failure, must be bad key file or bad user name", e);
        } catch (IOException e) {
            throw new ArtifactConnectException("Cannot connect to the host", e);
        }
    }

    private synchronized void checkConnection() {
        if (sshClient == null || !sshClient.isConnected() || !sshClient.isAuthenticated()) {
            log.info("Reconnecting the session for " + user + "@" + ip);
            initialize();
            log.info("Reconnected the session for " + user + "@" + ip);
        }
    }

    private static List<String> getSetEnvCommands(Map<String, String> env) {
        // Set envs
        if (env != null) {
            return env.entrySet().stream().filter(envEntry -> envEntry.getValue() != null).map(
                    envEntry -> "export " + envEntry.getKey() + "='" + envEntry.getValue() + "'"
            ).collect(Collectors.toList());
        } else {
            return new ArrayList<>();
        }
    }

    private static void sendCommands(PrintWriter commandWriter, String... commands) {
        for (String command : commands) {
            commandWriter.println(command);
        }
    }

    private static String readSheBang(Path script) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(script), "UTF-8"));
        String line = reader.readLine();
        while (line != null) {
            if (!StringUtils.isBlank(line)) {
                line = line.trim();
                if (line.startsWith("#!")) {
                    return line;
                } else {
                    // Not found return a default value
                    return "#!/bin/sh";
                }
            }
            line = reader.readLine();
        }
        // Not found return a default value
        return "#!/bin/sh";
    }

    private static void captureExitStatusAndEnvironmentVariables(PrintWriter commandWriter, String statusCodeToken, String environmentVariablesToken) {
        // Check exit code
        commandWriter.println("_toscaruntime_rc=$?");
        // Mark the beginning of environment variables
        commandWriter.println("echo " + environmentVariablesToken + "`printenv | sed -e ':a' -e 'N' -e '$!ba' -e 's/\\n/" + environmentVariablesToken + "/g'`");
        // Print env in order to be able to capture environment variables
        // Mark the beginning of status code
        commandWriter.println("echo " + statusCodeToken + "$_toscaruntime_rc");
    }

    private static Path createArtifactWrapper(String artifactPath, Map<String, String> env, String statusCodeToken, String environmentVariablesToken, String sheBang, boolean elevatePrivilege) throws IOException {
        Path localGeneratedScriptPath = Files.createTempFile("", ".sh");
        try (PrintWriter commandWriter = new PrintWriter(new BufferedOutputStream(Files.newOutputStream(localGeneratedScriptPath)))) {
            commandWriter.println(sheBang);
            if (elevatePrivilege) {
                commandWriter.println("sudo -s");
            }
            // Set env
            List<String> setEnvCommands = getSetEnvCommands(env);
            sendCommands(commandWriter, setEnvCommands.toArray(new String[setEnvCommands.size()]));
            // Make executable
            commandWriter.println("chmod +x " + artifactPath);
            // Launch the script
            commandWriter.println(". " + artifactPath);
            // Try to execute commands to capture exit status and environment variables
            captureExitStatusAndEnvironmentVariables(commandWriter, statusCodeToken, environmentVariablesToken);
            // Exit the ssh session
            if (elevatePrivilege) {
                commandWriter.println("exit");
            }
            commandWriter.println("exit");
            // If elevatePrivilege is enabled then perform sudo -s
            commandWriter.flush();
        }
        return localGeneratedScriptPath;
    }

    @Override
    public Map<String, String> executeArtifact(String operationName, Path localArtifactPath, String remoteArtifactPath, Map<String, String> env) {
        log.info("Begin to execute [{}][{}] with env [{}]", operationName, remoteArtifactPath, env);
        checkConnection();
        String artifactName = Paths.get(remoteArtifactPath).getFileName().toString();
        String statusCodeToken = UUID.randomUUID().toString();
        String environmentVariablesToken = UUID.randomUUID().toString();
        String remotePath;
        String sheBang;
        String shellPath;

        try {
            sheBang = readSheBang(localArtifactPath);
            // Remove #! from the shebang to get the path to shell binary
            shellPath = sheBang.substring(2);
            Path artifactWrapper = createArtifactWrapper(remoteArtifactPath, env, statusCodeToken, environmentVariablesToken, sheBang, elevatePrivilege);
            SCPFileTransfer scpFileTransfer = sshClient.newSCPFileTransfer();
            remotePath = REMOTE_TEMP_DIR + artifactWrapper.getFileName().toString();
            scpFileTransfer.upload(artifactWrapper.toString(), remotePath);
        } catch (IOException e) {
            throw new ArtifactExecutionException("[" + operationName + "][" + remoteArtifactPath + "] Error happened while trying to generate wrapper script", e);
        }
        Future<?> stdOutFuture;
        Future<?> stdErrFuture;
        Map<String, String> capturedEnvs;
        try (Session session = sshClient.startSession();
             SessionChannel shell = (SessionChannel) session.startShell();
             PrintWriter commandWriter = new PrintWriter(shell.getOutputStream())) {
            SSHJStdOutLogger stdOutLogger = new SSHJStdOutLogger(operationName, artifactName, log, statusCodeToken, environmentVariablesToken, shell.getInputStream());
            stdOutFuture = executorService.submit(stdOutLogger);
            stdErrFuture = executorService.submit(new SSHJStdErrLogger(operationName, artifactName, log, shell.getErrorStream()));
            commandWriter.println("chmod +x " + remotePath);
            commandWriter.println(shellPath + " < " + remotePath);
            // Try to execute commands to capture exit status and environment variables
            captureExitStatusAndEnvironmentVariables(commandWriter, statusCodeToken, environmentVariablesToken);
            commandWriter.println("exit");
            commandWriter.flush();
            while (true) {
                try {
                    session.join(5, TimeUnit.SECONDS);
                } catch (ConnectionException e) {
                    if (Thread.currentThread().isInterrupted()) {
                        log.info("Interrupted while waiting for script execution to finish");
                        throw e;
                    }
                }
                if (stdOutLogger.getStatusCode() != null) {
                    break;
                }
            }
            Integer statusCode = shell.getExitStatus();
            if (stdOutLogger.getStatusCode() != null) {
                statusCode = stdOutLogger.getStatusCode();
            }
            if (statusCode != 0) {
                throw new ArtifactExecutionException("[" + operationName + "][" + remoteArtifactPath + "] failed to executed with exit status " + statusCode);
            } else {
                log.info("[{}][{}] execution finished normally", operationName, artifactName);
            }
            capturedEnvs = stdOutLogger.getCapturedEnvVars();
        } catch (ConnectionException | TransportException e) {
            throw new ArtifactConnectException("[" + operationName + "][" + remoteArtifactPath + "] execution connection error", e);
        }
        try {
            stdOutFuture.get();
        } catch (Exception ignored) {
            log.error("Could not read artifact output", ignored);
        }
        try {
            stdErrFuture.get();
        } catch (Exception ignored) {
            log.error("Could not read artifact output", ignored);
        }
        return capturedEnvs;
    }

    private void prepareUpload(Session session, String command) {
        try (Session.Command sshCommand = session.exec(command)) {
            sshCommand.join();
            if (sshCommand.getExitStatus() != 0) {
                throw new ArtifactExecutionException("Command " + command + " failed with exit status " + sshCommand.getExitStatus());
            } else {
                log.info("Prepare upload finished normally with standard output [{}] and error output [{}]",
                        command,
                        new String(IOUtils.readFully(sshCommand.getInputStream()).toByteArray()),
                        new String(IOUtils.readFully(sshCommand.getErrorStream()).toByteArray()));
            }
        } catch (IOException e) {
            throw new ArtifactExecutionException("Command " + command + " has failed", e);
        }
    }

    @Override
    public void upload(String localPath, String remotePath) {
        checkConnection();
        try {
            Path parentPath = Paths.get(remotePath).getParent();
            String prepareCommand;
            if (parentPath != null) {
                // Delete if already exists and create the parent if given remote path has parent
                prepareCommand = "if [ -e \"" + remotePath + "\" ]; then echo \"Remote path [" + remotePath + "] already exist, will overwrite\"; rm -rf \"" + remotePath + "\"; else mkdir -p \"" + parentPath.toString() + "\"; fi";
            } else {
                // Delete if already exists
                prepareCommand = "if [ -e \"" + remotePath + "\" ]; then echo \"Remote path [" + remotePath + "] already exist, will overwrite\"; rm -rf \"" + remotePath + "\"; fi";
            }
            try (Session session = sshClient.startSession()) {
                prepareUpload(session, prepareCommand);
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
            if (sshClient != null) {
                sshClient.close();
                sshClient = null;
            }
        } catch (IOException e) {
            log.warn("Could not close ssh executor", e);
        }
    }
}
