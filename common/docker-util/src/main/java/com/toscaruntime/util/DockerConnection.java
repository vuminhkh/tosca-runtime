package com.toscaruntime.util;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.toscaruntime.artifact.BashArtifactExecutorUtil;
import com.toscaruntime.artifact.Connection;
import com.toscaruntime.artifact.OutputHandler;
import com.toscaruntime.artifact.SimpleOutputHandler;
import com.toscaruntime.exception.deployment.artifact.ArtifactExecutionException;
import com.toscaruntime.exception.deployment.artifact.ArtifactInterruptedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Docker executor is based on docker exec command
 *
 * @author Minh Khang VU
 */
public class DockerConnection implements Connection {

    private static final Logger log = LoggerFactory.getLogger(DockerConnection.class);

    private DockerClient dockerClient;

    private String containerId;

    /**
     * Run command and block until the end of execution, log all output to the given logger
     *
     * @param command command to be executed on the container
     * @param input   if the command has stdin
     */
    private Integer runCommand(String command, InputStream input) {
        DockerStreamDecoder dockerStreamDecoder = new DockerStreamDecoder();
        try (SimpleOutputHandler simpleOutputHandler = new SimpleOutputHandler()) {
            simpleOutputHandler.handleStdErr(dockerStreamDecoder.getStdErrStream());
            simpleOutputHandler.handleStdOut(dockerStreamDecoder.getStdOutStream());
            String execId = asyncRunCommand(command, input, dockerStreamDecoder);
            try {
                dockerStreamDecoder.awaitCompletion();
            } catch (InterruptedException e) {
                log.info("Command [{}] exec has been interrupted", command);
                throw new ArtifactInterruptedException("Command [" + command + "] exec has been interrupted", e);
            } catch (Exception e) {
                throw new ArtifactExecutionException("Command [" + command + "] exec encountered error", e);
            }
            return dockerClient.inspectExecCmd(execId).exec().getExitCode();
        }
    }

    private String asyncRunCommand(String command, InputStream input, DockerStreamDecoder outputDecoder) {
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
        execStartCmd.exec(outputDecoder);
        return execCreateCmdResponse.getId();
    }

    @Override
    public void upload(String localPath, String remotePath) {
        String prepareCommand = "if [ -e \"" + remotePath + "\" ]; then echo \"Remote path [" + remotePath + "] already exist, will overwrite\"; rm -rf \"" + remotePath + "\"; mkdir -p \"" + remotePath + "\"; else mkdir -p \"" + remotePath + "\"; fi";
        executeCommand(prepareCommand);
        dockerClient.copyArchiveToContainerCmd(containerId).withHostResource(localPath).withDirChildrenOnly(true).withRemotePath(remotePath).exec();
    }

    @Override
    public Integer executeCommand(String command) {
        return runCommand("/bin/sh", new ByteArrayInputStream(command.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public Integer executeScript(String scriptContent, Map<String, String> variables, OutputHandler outputHandler) {
        try (DockerStreamDecoder dockerStreamDecoder = new DockerStreamDecoder()) {
            StringWriter rawWriter = new StringWriter();
            PrintWriter commandWriter = new PrintWriter(rawWriter);
            List<String> setEnvCommands = BashArtifactExecutorUtil.getSetEnvCommands(variables);
            setEnvCommands.forEach(commandWriter::println);
            commandWriter.write(scriptContent);
            outputHandler.handleStdErr(dockerStreamDecoder.getStdErrStream());
            outputHandler.handleStdOut(dockerStreamDecoder.getStdOutStream());
            String execId = asyncRunCommand(BashArtifactExecutorUtil.readInterpreterCommand(new StringReader(scriptContent)), new ByteArrayInputStream(rawWriter.toString().getBytes(StandardCharsets.UTF_8)), dockerStreamDecoder);
            dockerStreamDecoder.awaitCompletion();
            return dockerClient.inspectExecCmd(execId).exec().getExitCode();
        } catch (InterruptedException e) {
            log.info("Script execution has been interrupted", e);
            throw new ArtifactInterruptedException("Script execution has been interrupted", e);
        } catch (Exception e) {
            throw new ArtifactExecutionException("Script execution exec encountered error", e);
        }
    }

    @Override
    public void initialize(Map<String, Object> properties) {
        this.dockerClient = DockerUtil.buildDockerClient(PropertyUtil.getMandatoryPropertyAsString(properties, "docker_url"), PropertyUtil.getPropertyAsString(properties, "cert_path"));
        this.containerId = PropertyUtil.getMandatoryPropertyAsString(properties, "container_id");
    }

    @Override
    public void close() throws IOException {
    }
}
