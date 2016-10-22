package com.toscaruntime.util;

import com.toscaruntime.artifact.Connection;
import com.toscaruntime.artifact.ConnectionUtil;
import com.toscaruntime.artifact.OperationOutput;
import com.toscaruntime.artifact.OutputHandler;
import com.toscaruntime.artifact.SimpleOutputHandler;
import com.toscaruntime.exception.InterruptedByUserException;
import com.toscaruntime.exception.OperationNotImplementedException;
import com.toscaruntime.exception.deployment.artifact.ArtifactAuthenticationFailureException;
import com.toscaruntime.exception.deployment.artifact.ArtifactConnectException;
import com.toscaruntime.exception.deployment.artifact.ArtifactUploadException;
import com.toscaruntime.exception.deployment.artifact.BadExecutorConfigurationException;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.Factory;
import net.schmizz.sshj.common.SSHException;
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

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SSHConnection implements Connection {

    private static final Logger log = LoggerFactory.getLogger(SSHConnection.class);

    private SSHClient sshClient;

    private String user;

    private String ip;

    private int port;

    private String pemPath;

    private String pemContent;

    private int connectRetry;

    private long waitBetweenConnectRetry;

    @Override
    public void initialize(Map<String, Object> properties) {
        this.user = PropertyUtil.getMandatoryPropertyAsString(properties, Connection.USER);
        this.ip = PropertyUtil.getMandatoryPropertyAsString(properties, Connection.TARGET);
        this.port = Integer.parseInt(PropertyUtil.getMandatoryPropertyAsString(properties, Connection.PORT));
        this.pemPath = PropertyUtil.getPropertyAsString(properties, Connection.KEY_PATH);
        this.pemContent = PropertyUtil.getPropertyAsString(properties, Connection.KEY_CONTENT);
        if (StringUtils.isBlank(this.pemContent) && StringUtils.isBlank(this.pemPath)) {
            throw new BadExecutorConfigurationException("Executor is not configured properly, one of key_path or key_content is expected");
        }
        this.connectRetry = getConnectRetry(properties);
        this.waitBetweenConnectRetry = getWaitBetweenConnectRetry(properties);
        log.info("Initializing SSH connection " + toString());
        establishConnection();
    }

    private static int getConnectRetry(Map<String, Object> properties) {
        return Integer.parseInt(PropertyUtil.getPropertyAsString(properties, "connect_retry", "720"));
    }

    private static long getWaitBetweenConnectRetry(Map<String, Object> properties) {
        String waitBetweenConnectRetry = PropertyUtil.getPropertyAsString(properties, "wait_between_connect_retry", "5 s");
        return ToscaUtil.convertToSeconds(waitBetweenConnectRetry);
    }

    private void establishConnection() {
        FailSafeUtil.doActionWithRetryNoCheckedException(this::doEstablishConnection, "Establish connection to [" + ip + "]", this.connectRetry, this.waitBetweenConnectRetry, TimeUnit.SECONDS, ArtifactConnectException.class);
    }

    private void doEstablishConnection() {
        // Trust every host
        try {
            sshClient = new SSHClient();
            sshClient.addHostKeyVerifier((h, p, k) -> true);
            sshClient.connect(ip, port);
            if (StringUtils.isNotBlank(pemPath)) {
                log.info("Use key at [" + pemPath + "] to connect to the machine [" + ip + "]");
                sshClient.authPublickey(user, pemPath);
            } else {
                log.info("Use key content with length [" + pemContent.length() + "] to connect to the machine [" + ip + "]");
                KeyFormat format = KeyProviderUtil.detectKeyFileFormat(pemContent, false);
                final FileKeyProvider fkp =
                        Factory.Named.Util.create(sshClient.getTransport().getConfig().getFileKeyProviderFactories(), format.toString());
                if (fkp == null)
                    throw new SSHException("No provider available for " + format + " key file");
                fkp.init(pemContent, null);
                sshClient.authPublickey(user, fkp);
            }
        } catch (UserAuthException e) {
            ExceptionUtil.checkInterrupted(e);
            throw new ArtifactAuthenticationFailureException("User authentication failure : " + e.getMessage(), e);
        } catch (IOException e) {
            ExceptionUtil.checkInterrupted(e);
            if (e.getMessage() != null) {
                throw new ArtifactConnectException("IO failure : " + e.getMessage(), e);
            } else {
                throw new ArtifactConnectException(null, e);
            }
        }
    }

    @Override
    public Integer executeCommand(String command) {
        try (SimpleOutputHandler outputHandler = new SimpleOutputHandler()) {
            return executeCommand(command, outputHandler);
        }
    }

    @Override
    public Integer executeCommand(String command, OutputHandler outputHandler) {
        checkConnection();
        try (Session session = sshClient.startSession();
             Session.Command sshCommand = session.exec(command)) {
            sshCommand.join();
            outputHandler.handleStdOut(sshCommand.getInputStream());
            outputHandler.handleStdErr(sshCommand.getErrorStream());
            if (sshCommand.getExitStatus() == null || sshCommand.getExitStatus() != 0) {
                log.error("Execute command [{}] finished with error status [{}]", command, sshCommand.getExitStatus());
            } else {
                log.info("Execute command [{}] finished normally", command);
            }
            return sshCommand.getExitStatus();
        } catch (ConnectionException | TransportException e) {
            ExceptionUtil.checkInterrupted(e);
            throw new ArtifactConnectException("Cannot execute command [" + command + "], encountered connection error", e);
        }
    }

    @Override
    public Integer executeRemoteArtifact(String scriptPath, Map<String, Object> variables, OutputHandler outputHandler) {
        throw new OperationNotImplementedException("Method not implemented");
    }

    @Override
    public Integer executeArtifact(String scriptContent, Map<String, Object> variables, OutputHandler outputHandler) {
        checkConnection();
        try (Session session = sshClient.startSession();
             SessionChannel shell = (SessionChannel) session.startShell();
             PrintWriter commandWriter = new PrintWriter(shell.getOutputStream())) {
            outputHandler.handleStdOut(shell.getInputStream());
            outputHandler.handleStdErr(shell.getErrorStream());
            List<String> setEnvCommands = ConnectionUtil.getSetEnvCommands(variables);
            setEnvCommands.forEach(commandWriter::println);
            commandWriter.println(scriptContent);
            commandWriter.println("exit");
            commandWriter.flush();
            while (true) {
                try {
                    session.join(5, TimeUnit.SECONDS);
                } catch (ConnectionException e) {
                    boolean isInterrupted = ExceptionUtils.indexOfType(e, InterruptedException.class) >= 0;
                    if (isInterrupted) {
                        log.info("Script execution has been interrupted", e);
                        throw new InterruptedByUserException("Execution has been interrupted", e);
                    }
                }
                OperationOutput operationOutput = outputHandler.tryGetOperationOutput();
                if (operationOutput != null && operationOutput.getStatusCode() != null) {
                    break;
                }
                if (shell.getExitStatus() != null) {
                    break;
                }
            }
            return shell.getExitStatus();
        } catch (ConnectionException | TransportException e) {
            ExceptionUtil.checkInterrupted(e);
            throw new ArtifactConnectException("Execution connection error", e);
        }
    }

    private synchronized void checkConnection() {
        if (sshClient == null || !sshClient.isConnected() || !sshClient.isAuthenticated()) {
            log.info("Reconnecting the session for " + user + "@" + ip);
            close();
            establishConnection();
            log.info("Reconnected the session for " + user + "@" + ip);
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
        try (SimpleOutputHandler outputHandler = new SimpleOutputHandler()) {
            String prepareCommand = getPrepareUploadArtifactCommand(remotePath);
            executeCommand(prepareCommand, outputHandler);
            SCPFileTransfer scpFileTransfer = sshClient.newSCPFileTransfer();
            scpFileTransfer.upload(localPath, remotePath);
        } catch (ConnectionException | TransportException e) {
            ExceptionUtil.checkInterrupted(e);
            throw new ArtifactConnectException("Upload [" + localPath + "] to [" + remotePath + "] encountered connection error", e);
        } catch (IOException e) {
            ExceptionUtil.checkInterrupted(e);
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

    @Override
    public String toString() {
        return "SSHConnection{" +
                "user='" + user + '\'' +
                ", ip='" + ip + '\'' +
                ", port=" + port +
                ", pemPath='" + pemPath + '\'' +
                ", pemContent='" + (pemContent != null ? pemContent.length() : 0) + '\'' +
                ", connectRetry=" + connectRetry +
                ", waitBetweenConnectRetry=" + waitBetweenConnectRetry +
                '}';
    }
}
