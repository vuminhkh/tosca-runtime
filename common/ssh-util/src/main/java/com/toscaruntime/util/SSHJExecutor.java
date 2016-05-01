package com.toscaruntime.util;

import com.toscaruntime.artifact.ArtifactExecutor;
import com.toscaruntime.artifact.ArtifactExecutorUtil;
import com.toscaruntime.artifact.ArtifactUploader;
import com.toscaruntime.exception.deployment.artifact.*;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.Factory;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.common.SSHException;
import net.schmizz.sshj.common.SecurityUtils;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.SessionChannel;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.userauth.UserAuthException;
import net.schmizz.sshj.userauth.keyprovider.FileKeyProvider;
import net.schmizz.sshj.userauth.keyprovider.KeyFormat;
import net.schmizz.sshj.userauth.keyprovider.KeyProviderUtil;
import net.schmizz.sshj.xfer.scp.SCPFileTransfer;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Security;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

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

    private SSHClient sshClient;

    private String user;

    private String ip;

    private int port;

    private String pemPath;

    private String pemContent;

    private boolean elevatePrivilege;

    public SSHJExecutor(String user, String ip, int port, Path pemPath, boolean elevatePrivilege) {
        this.user = user;
        this.ip = ip;
        this.port = port;
        this.pemPath = pemPath.toString();
        this.elevatePrivilege = elevatePrivilege;
    }

    public SSHJExecutor(String user, String ip, int port, String pemContent, boolean elevatePrivilege) {
        this.user = user;
        this.ip = ip;
        this.port = port;
        this.pemContent = pemContent;
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
            if (StringUtils.isNotBlank(pemPath)) {
                sshClient.authPublickey(user, pemPath);
            } else {
                KeyFormat format = KeyProviderUtil.detectKeyFileFormat(pemContent, false);
                final FileKeyProvider fkp =
                        Factory.Named.Util.create(sshClient.getTransport().getConfig().getFileKeyProviderFactories(), format.toString());
                if (fkp == null)
                    throw new SSHException("No provider available for " + format + " key file");
                fkp.init(pemContent, null);
                sshClient.authPublickey(user, fkp);
            }
        } catch (UserAuthException e) {
            throw new ArtifactAuthenticationFailureException("User authentication failure : " + e.getMessage(), e);
        } catch (IOException e) {
            throw new ArtifactConnectException("IO failure : " + e.getMessage(), e);
        }
    }

    private synchronized void checkConnection() {
        if (sshClient == null || !sshClient.isConnected() || !sshClient.isAuthenticated()) {
            log.info("Reconnecting the session for " + user + "@" + ip);
            initialize();
            log.info("Reconnected the session for " + user + "@" + ip);
        }
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

        try {
            sheBang = ArtifactExecutorUtil.readSheBang(localArtifactPath);
            Path artifactWrapper = ArtifactExecutorUtil.createArtifactWrapper(remoteArtifactPath, env, statusCodeToken, environmentVariablesToken, sheBang, elevatePrivilege);
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
            // Trigger execution of the artifact
            ArtifactExecutorUtil.createArtifactExecutor(commandWriter, remotePath, sheBang, statusCodeToken, environmentVariablesToken);
            while (true) {
                try {
                    session.join(5, TimeUnit.SECONDS);
                } catch (ConnectionException e) {
                    boolean isInterrupted = ExceptionUtils.indexOfType(e, InterruptedException.class) >= 0;
                    if (isInterrupted) {
                        stdErrFuture.cancel(true);
                        stdOutFuture.cancel(true);
                        log.info("[{}][{}] execution has been interrupted", operationName, artifactName);
                        throw new ArtifactInterruptedException("Execution has been interrupted", e);
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
            if (ExceptionUtils.indexOfType(e, InterruptedException.class) >= 0) {
                throw new ArtifactInterruptedException("Execution has been interrupted", e);
            } else {
                throw new ArtifactExecutionException("Command " + command + " has failed", e);
            }
        }
    }

    private static String getPrepareUploadArtifactCommand(String remotePath) {
        String pathToCreateDirectory;
        Path parentPath = Paths.get(remotePath).getParent();
        if (parentPath != null) {
            pathToCreateDirectory = parentPath.toString();
        } else {
            pathToCreateDirectory = null;
        }
        if (pathToCreateDirectory != null) {
            // Delete if already exists and create the parent if given remote path has parent
            return "if [ -e \"" + remotePath + "\" ]; then echo \"Remote path [" + remotePath + "] already exist, will overwrite\"; rm -rf \"" + remotePath + "\"; mkdir -p \"" + pathToCreateDirectory + "\"; else mkdir -p \"" + pathToCreateDirectory + "\"; fi";
        } else {
            return "if [ -e \"" + remotePath + "\" ]; then echo \"Remote path [" + remotePath + "] already exist, will overwrite\"; rm -rf \"" + remotePath + "\"; fi";
        }
    }

    @Override
    public void upload(String localPath, String remotePath) {
        checkConnection();
        try {
            String prepareCommand = getPrepareUploadArtifactCommand(remotePath);
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
