package com.toscaruntime.connection;

import com.toscaruntime.artifact.Connection;
import com.toscaruntime.artifact.OutputHandler;
import com.toscaruntime.artifact.SimpleCommandOutputHandler;
import com.toscaruntime.exception.deployment.artifact.ArtifactExecutionException;
import com.toscaruntime.util.ExceptionUtil;
import com.toscaruntime.util.PropertyUtil;
import com.toscaruntime.util.ScalaFileUtil;
import com.toscaruntime.util.StreamUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class LocalConnection implements Connection {

    private static final Logger log = LoggerFactory.getLogger(LocalConnection.class);

    private String shell;

    @Override
    public void initialize(Map<String, Object> properties) {
        log.info("Initializing local connection");
        this.shell = PropertyUtil.getPropertyAsString(properties, SHELL, "/bin/sh");
    }

    @Override
    public void upload(String localPath, String remoteDirectory) {
        if (!Objects.equals(localPath, remoteDirectory)) {
            Path localNioPath = Paths.get(localPath);
            Path remotePath = Paths.get(remoteDirectory);
            if (Files.isRegularFile(localNioPath)) {
                remotePath = remotePath.resolve(localNioPath.getFileName());
            }
            try {
                ScalaFileUtil.copyRecursive(localNioPath, remotePath);
            } catch (Exception e) {
                throw new ArtifactExecutionException("Could not copy file", e);
            }
        }
    }

    @Override
    public Integer executeArtifact(String artifactContent, Map<String, Object> variables, OutputHandler outputHandler) {
        Path tempArtifactPath;
        try {
            tempArtifactPath = Files.createTempFile("toscaruntime_connection", "");
            Files.write(tempArtifactPath, artifactContent.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ArtifactExecutionException("Could not write artifact content to temp file", e);
        }
        return executeRemoteArtifact(tempArtifactPath.toString(), variables, outputHandler);
    }

    @Override
    public Integer executeCommand(String command) {
        try (SimpleCommandOutputHandler outputHandler = new SimpleCommandOutputHandler(command, log)) {
            return executeCommand(command, outputHandler);
        }
    }

    @Override
    public Integer executeCommand(String command, OutputHandler outputHandler) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(this.shell);
            processBuilder.environment().putAll(System.getenv());
            Process process = processBuilder.start();
            try (OutputStream commandInput = process.getOutputStream()) {
                commandInput.write(command.getBytes());
            }
            outputHandler.handleStdOut(process.getInputStream());
            outputHandler.handleStdErr(process.getErrorStream());
            outputHandler.waitForOutputToBeConsumed();
            return process.waitFor();
        } catch (Exception e) {
            ExceptionUtil.checkInterrupted(e);
            throw new ArtifactExecutionException("Unable to execute command [" + command + "]", e);
        }
    }

    @Override
    public Integer executeRemoteArtifact(String artifactPath, Map<String, Object> variables, OutputHandler outputHandler) {
        try {
            Path artifact = Paths.get(artifactPath);
            if (!Files.isExecutable(artifact)) {
                Set<PosixFilePermission> currentPermissions = Files.getPosixFilePermissions(artifact);
                currentPermissions.add(PosixFilePermission.OWNER_EXECUTE);
                currentPermissions.add(PosixFilePermission.GROUP_EXECUTE);
                Files.setPosixFilePermissions(artifact, currentPermissions);
            }
            ProcessBuilder processBuilder = new ProcessBuilder(artifactPath);
            processBuilder.environment().putAll(System.getenv());
            processBuilder.environment().putAll(StreamUtil.safeEntryStream(variables).collect(Collectors.toMap(Map.Entry::getKey, entry -> PropertyUtil.propertyValueToString(entry.getValue()))));
            Process process = processBuilder.start();
            outputHandler.handleStdOut(process.getInputStream());
            outputHandler.handleStdErr(process.getErrorStream());
            outputHandler.waitForOutputToBeConsumed();
            return process.waitFor();
        } catch (Exception e) {
            ExceptionUtil.checkInterrupted(e);
            throw new ArtifactExecutionException("Unable to execute artifact [" + artifactPath + "]", e);
        }
    }

    @Override
    public void close() {
        log.info("Closing local connection");
    }
}
