package com.toscaruntime.util;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.core.async.ResultCallbackTemplate;
import com.toscaruntime.artifact.ArtifactExecutor;
import com.toscaruntime.artifact.ArtifactExecutorUtil;
import com.toscaruntime.artifact.ArtifactUploader;
import com.toscaruntime.exception.deployment.artifact.ArtifactExecutionException;
import com.toscaruntime.exception.deployment.artifact.ArtifactInterruptedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

/**
 * Docker executor is based on docker exec command
 *
 * @author Minh Khang VU
 */
public class DockerExecutor implements Closeable, ArtifactExecutor, ArtifactUploader {

    private static final Logger log = LoggerFactory.getLogger(DockerExecutor.class);

    private DockerClient dockerClient;

    private String containerId;

    private boolean elevatePrivilege;

    public DockerExecutor(DockerClient dockerClient, String containerId, boolean elevatePrivilege) {
        this.dockerClient = dockerClient;
        this.containerId = containerId;
        this.elevatePrivilege = elevatePrivilege;
    }

    @Override
    public void initialize() {
    }

    @Override
    public Map<String, String> executeArtifact(String operationName, Path localArtifactPath, String remoteArtifactPath, Map<String, String> env) {
        log.info("Begin to execute [{}][{}] with env [{}]", operationName, remoteArtifactPath, env);
        String artifactName = Paths.get(remoteArtifactPath).getFileName().toString();
        String statusCodeToken = UUID.randomUUID().toString();
        String environmentVariablesToken = UUID.randomUUID().toString();
        String remotePath;
        String sheBang;
        try {
            sheBang = ArtifactExecutorUtil.readSheBang(localArtifactPath);
            Path artifactWrapper = ArtifactExecutorUtil.createArtifactWrapper(remoteArtifactPath, env, statusCodeToken, environmentVariablesToken, sheBang, elevatePrivilege);
            remotePath = REMOTE_TEMP_DIR + artifactWrapper.getFileName().toString();
            dockerClient.copyArchiveToContainerCmd(containerId).withHostResource(artifactWrapper.toString()).withRemotePath(REMOTE_TEMP_DIR).exec();
        } catch (IOException e) {
            throw new ArtifactExecutionException("[" + operationName + "][" + remoteArtifactPath + "] Error happened while trying to generate wrapper script", e);
        }
        // Not need to close byte array streams as they are in memory
        ByteArrayOutputStream scriptToExecute = new ByteArrayOutputStream();
        PrintWriter commandWriter = new PrintWriter(new OutputStreamWriter(scriptToExecute, StandardCharsets.UTF_8), true);
        ArtifactExecutorUtil.createArtifactExecutor(commandWriter, remotePath, sheBang, statusCodeToken, environmentVariablesToken);

        DockerStdOutLogger dockerStdOutLogger = new DockerStdOutLogger(statusCodeToken, environmentVariablesToken);
        CommandLogger commandLogger = line -> {
            switch (line.getStreamType()) {
                case STDOUT:
                case RAW:
                    String processedLine = dockerStdOutLogger.processNewLine(line.getData());
                    if (processedLine != null) {
                        log.info("[{}][{}][stdout] {}", operationName, artifactName, line.getData());
                    }
                    break;
                default:
                    log.info("[{}][{}][stderr] {}", operationName, artifactName, line.getData());
                    break;
            }
        };
        Integer statusCode = runCommand("/bin/sh", commandLogger, new ByteArrayInputStream(scriptToExecute.toByteArray()));
        if (dockerStdOutLogger.getStatusCode() != null) {
            statusCode = dockerStdOutLogger.getStatusCode();
        }
        if (statusCode != 0) {
            throw new ArtifactExecutionException("[" + operationName + "][" + remoteArtifactPath + "] failed to executed with exit status [" + statusCode + "]");
        } else {
            log.info("[{}][{}] execution finished normally", operationName, artifactName);
        }
        return dockerStdOutLogger.getCapturedEnvVars();
    }

    private String getStreamType(StreamType streamType) {
        switch (streamType) {
            case RAW:
            case STDOUT:
                return "stdout";
            default:
                return "stderr";
        }
    }

    public void runCommand(String operationName, String command, Logger log) {
        Logger loggerToUse = log != null ? log : DockerExecutor.log;
        if (elevatePrivilege) {
            command = "sudo -s\n" + command + "\nexit\n";
        }
        Integer exitStatus = runCommand("/bin/sh", line -> loggerToUse.info("[" + operationName + "][" + getStreamType(line.getStreamType()) + "]: " + line.getData()), new ByteArrayInputStream(command.getBytes(StandardCharsets.UTF_8)));
        if (exitStatus != null && exitStatus != 0) {
            throw new ArtifactExecutionException("Script " + command + " exec has exited with error status " + exitStatus + " for container " + containerId);
        }
    }

    /**
     * Run command and block until the end of execution, log all output to the given logger
     *
     * @param command command to be executed on the container
     * @param log     command output will be logged via this logger
     * @param input   if the command has stdin
     */
    public Integer runCommand(String command, CommandLogger log, InputStream input) {
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withCmd(command.split("\\s"))
                .exec();
        ExecStartCmd execStartCmd = dockerClient.execStartCmd(containerId)
                .withTty(false)
                .withExecId(execCreateCmdResponse.getId())
                .withDetach(false);
        if (input != null) {
            execStartCmd.withStdIn(input);
        }
        try (ResultCallbackTemplate resultCallback = execStartCmd.exec(new DockerStreamDecoder(log))) {
            resultCallback.awaitCompletion();
        } catch (InterruptedException e) {
            DockerExecutor.log.info("Command [{}] exec has been interrupted", command);
            throw new ArtifactInterruptedException("Script " + command + " exec has been interrupted", e);
        } catch (Exception e) {
            throw new ArtifactExecutionException("Script " + command + " exec encountered error", e);
        }
        InspectExecResponse response = dockerClient.inspectExecCmd(execCreateCmdResponse.getId()).exec();
        return response.getExitCode();
    }

    @Override
    public void upload(String localPath, String remotePath) {
        String prepareCommand = "if [ -e \"" + remotePath + "\" ]; then echo \"Remote path [" + remotePath + "] already exist, will overwrite\"; rm -rf \"" + remotePath + "\"; mkdir -p \"" + remotePath + "\"; else mkdir -p \"" + remotePath + "\"; fi";
        runCommand("Prepare recipe dir for upload", prepareCommand, log);
        dockerClient.copyArchiveToContainerCmd(containerId).withHostResource(localPath).withDirChildrenOnly(true).withRemotePath(remotePath).exec();
    }

    @Override
    public void close() throws IOException {
    }
}
