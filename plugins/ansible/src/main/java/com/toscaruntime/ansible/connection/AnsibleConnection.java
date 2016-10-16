package com.toscaruntime.ansible.connection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.toscaruntime.artifact.CommandUtil;
import com.toscaruntime.artifact.Connection;
import com.toscaruntime.artifact.OutputHandler;
import com.toscaruntime.exception.OperationNotImplementedException;
import com.toscaruntime.exception.UnexpectedException;
import com.toscaruntime.exception.deployment.artifact.ArtifactIOException;
import com.toscaruntime.util.JSONUtil;
import com.toscaruntime.util.PropertyUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

public abstract class AnsibleConnection implements Connection {

    public static final String CONTROL_MACHINE_CONNECTION = "control_machine_connection";

    private static final Logger log = LoggerFactory.getLogger(AnsibleConnection.class);

    private Connection controlMachineConnection;

    private String target;

    private String user;

    private Integer port;

    private Path userDataDir;

    private String remoteKeyFile;

    private String connectionType;

    private String ansibleBinPath;

    private String ansiblePlaybookBinPath;

    @Override
    public void initialize(Map<String, Object> properties) {
        this.controlMachineConnection = (Connection) PropertyUtil.getMandatoryProperty(properties, CONTROL_MACHINE_CONNECTION);
        this.target = PropertyUtil.getMandatoryPropertyAsString(properties, Connection.TARGET);
        this.user = PropertyUtil.getPropertyAsString(properties, Connection.USER);
        String portRaw = PropertyUtil.getPropertyAsString(properties, Connection.PORT);
        this.port = StringUtils.isNotBlank(portRaw) ? Integer.parseInt(portRaw) : null;
        String dataDir = PropertyUtil.getPropertyAsString(properties, "data_dir", Paths.get(getHomeDirOnControlMachine()).resolve(".toscaruntime").toString());
        this.userDataDir = Paths.get(dataDir).resolve(this.target.replaceAll("\\W", "_"));
        // Create folder for keys
        String pemPath = PropertyUtil.getPropertyAsString(properties, Connection.KEY_PATH);
        String pemContent = PropertyUtil.getPropertyAsString(properties, Connection.KEY_CONTENT);
        if (StringUtils.isNotBlank(pemPath)) {
            controlMachineConnection.upload(pemPath, this.userDataDir.toString());
            moveKey(Paths.get(pemPath));
        } else if (StringUtils.isNotBlank(pemContent)) {
            try {
                Path temporaryKeyFile = writeToTempFile(pemContent);
                controlMachineConnection.upload(temporaryKeyFile.toString(), this.userDataDir.toString());
                moveKey(temporaryKeyFile);
            } catch (IOException e) {
                throw new ArtifactIOException("Error creating temp file", e);
            }
        }
        this.connectionType = PropertyUtil.getPropertyAsString(properties, "connection_type");
        this.ansibleBinPath = PropertyUtil.getPropertyAsString(properties, "ansible_bin_path", "ansible");
        this.ansiblePlaybookBinPath = PropertyUtil.getPropertyAsString(properties, "ansible_playbook_bin_path", "ansible-playbook");
    }

    private String getHomeDirOnControlMachine() {
        return CommandUtil.evaluate(controlMachineConnection, "echo $HOME");
    }

    private Path writeToTempFile(String content) throws IOException {
        Path temporaryKeyFile = Files.createTempFile("", "");
        Files.write(temporaryKeyFile, content.getBytes(StandardCharsets.UTF_8));
        return temporaryKeyFile;
    }

    private void moveKey(Path pemPath) {
        this.remoteKeyFile = this.userDataDir.resolve("_toscaruntime.pem").toString();
        executeCommandOnControlMachine("mv " + this.userDataDir.resolve(pemPath.getFileName()).toString() + " " + this.remoteKeyFile + " && chmod 0400 " + this.remoteKeyFile);
    }

    private void executeCommandOnControlMachine(String command) {
        Integer commandStatus = controlMachineConnection.executeCommand(command);
        if (commandStatus == null || commandStatus != 0) {
            throw new UnexpectedException("Unable to execute command on the control machine [" + command + "]");
        }
    }

    @Override
    public void upload(String localPath, String remotePath) {
        // Upload to control machine and not the target
        controlMachineConnection.upload(localPath, this.userDataDir.resolve(remotePath).toString());
    }

    private String getTargetWithPort() {
        String targetWithPort = target;
        if (this.port != null) {
            return String.format("%s:%s,", target, this.port.toString());
        } else {
            return targetWithPort + ",";
        }
    }

    protected String appendOption(String command, String optionName, String optionValue) {
        if (StringUtils.isNotBlank(optionValue)) {
            return command + String.format(" --%s=%s", optionName, optionValue);
        } else {
            return command;
        }
    }

    protected String exportVariable(String command, String variableName, String variableValue) {
        if (StringUtils.isNotBlank(variableValue)) {
            return String.format("export %s=%s; ", variableName, variableValue) + command;
        } else {
            return command;
        }
    }

    protected String appendConnectionInfo(String ansibleCommand) {
        ansibleCommand = exportVariable(ansibleCommand, "ANSIBLE_HOST_KEY_CHECKING", "False");
        ansibleCommand = appendOption(ansibleCommand, "inventory-file", getTargetWithPort());
        ansibleCommand = appendOption(ansibleCommand, "user", this.user);
        ansibleCommand = appendOption(ansibleCommand, "private-key", this.remoteKeyFile);
        ansibleCommand = appendOption(ansibleCommand, "connection", this.connectionType);
        return ansibleCommand;
    }

    private String wrapCommandWithAnsible(String command) {
        String ansibleCommand = this.ansibleBinPath + " all";
        ansibleCommand = appendConnectionInfo(ansibleCommand);
        ansibleCommand = appendOption(ansibleCommand, "module-name", "shell");
        ansibleCommand = appendOption(ansibleCommand, "args", String.format("'%s'", command.replace("'", "'\"'\"'")));
        if (log.isDebugEnabled()) {
            log.debug("Execute command with [{}]", ansibleCommand);
        }
        return ansibleCommand;
    }

    @Override
    public Integer executeCommand(String command) {
        String ansibleCommand = wrapCommandWithAnsible(command);
        return controlMachineConnection.executeCommand(ansibleCommand);
    }

    @Override
    public Integer executeCommand(String command, OutputHandler outputHandler) {
        String ansibleCommand = wrapCommandWithAnsible(command);
        return controlMachineConnection.executeCommand(ansibleCommand, outputHandler);
    }

    public Map<String, Object> getFacts(String factPath) {
        String factsRawOutput = CommandUtil.evaluate(controlMachineConnection, "cat " + this.userDataDir.resolve(factPath));
        int indexOfAccolade = factsRawOutput.indexOf('{');
        if (indexOfAccolade >= 0) {
            return PropertyUtil.toMap(factsRawOutput.substring(indexOfAccolade, factsRawOutput.length()));
        } else {
            return Collections.emptyMap();
        }
    }

    private String wrapPlaybookWithAnsible(String playbookPath, Map<String, String> variables) {
        String ansibleCommand = this.ansiblePlaybookBinPath;
        ansibleCommand = appendConnectionInfo(ansibleCommand);
        if (variables != null && !variables.isEmpty()) {
            try {
                ansibleCommand = appendOption(ansibleCommand, "extra-vars", "'" + JSONUtil.toString(variables) + "'");
            } catch (JsonProcessingException e) {
                throw new UnexpectedException("Could not serialize environment variables for playbook", e);
            }
        }
        ansibleCommand += " " + playbookPath;
        if (log.isDebugEnabled()) {
            log.debug("Execute playbook with [{}]", ansibleCommand);
        }
        return ansibleCommand;
    }

    @Override
    public Integer executeRemoteArtifact(String playbookPath, Map<String, String> variables, OutputHandler outputHandler) {
        return controlMachineConnection.executeCommand(wrapPlaybookWithAnsible(this.userDataDir.resolve(playbookPath).toString(), variables), outputHandler);
    }

    @Override
    public Integer executeArtifact(String playbookContent, Map<String, String> variables, OutputHandler outputHandler) {
        throw new OperationNotImplementedException("Method not implemented");
    }

    @Override
    public void close() {
        controlMachineConnection.close();
    }

    public String getUser() {
        return user;
    }

    public Path getUserDataDir() {
        return userDataDir;
    }

    public String getRemoteKeyFile() {
        return remoteKeyFile;
    }

    public String getConnectionType() {
        return connectionType;
    }

    public void setConnectionType(String connectionType) {
        this.connectionType = connectionType;
    }
}
