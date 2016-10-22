package com.toscaruntime.ansible.connection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.toscaruntime.artifact.CommandUtil;
import com.toscaruntime.artifact.Connection;
import com.toscaruntime.artifact.OutputHandler;
import com.toscaruntime.constant.InputConstant;
import com.toscaruntime.exception.OperationNotImplementedException;
import com.toscaruntime.exception.UnexpectedException;
import com.toscaruntime.exception.deployment.artifact.ArtifactIOException;
import com.toscaruntime.exception.deployment.configuration.PropertyAccessException;
import com.toscaruntime.util.JSONUtil;
import com.toscaruntime.util.PropertyUtil;
import com.toscaruntime.util.ToscaUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class AnsibleConnection implements Connection {

    private static final Logger log = LoggerFactory.getLogger(AnsibleConnection.class);

    public static final String CONTROL_MACHINE_CONNECTION = "control_machine_connection";
    public static final String DATA_DIR = "data_dir";
    public static final String ANSIBLE_COMMAND = "ansible_command";
    public static final String ANSIBLE_PLAYBOOK_COMMAND = "ansible_playbook_command";
    public static final String CONNECT_TIMEOUT = "connect_timeout";
    public static final String DELAY = "delay";

    private Connection controlMachineConnection;

    private String target;

    private String user;

    private Integer port;

    private Path userDataDir;

    private String remoteKeyFile;

    private String connectionType;

    private String ansibleBinPath;

    private String ansiblePlaybookBinPath;

    private long connectTimeout;

    private long delay;

    @Override
    public void initialize(Map<String, Object> properties) {
        this.controlMachineConnection = (Connection) PropertyUtil.getMandatoryProperty(properties, CONTROL_MACHINE_CONNECTION);
        this.target = PropertyUtil.getMandatoryPropertyAsString(properties, Connection.TARGET);
        this.user = PropertyUtil.getPropertyAsString(properties, Connection.USER);
        String portRaw = PropertyUtil.getPropertyAsString(properties, Connection.PORT);
        this.port = StringUtils.isNotBlank(portRaw) ? Integer.parseInt(portRaw) : null;
        String dataDir = PropertyUtil.getPropertyAsString(properties, DATA_DIR, Paths.get(getHomeDirOnControlMachine()).resolve(".toscaruntime").toString());
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
        this.connectionType = PropertyUtil.getPropertyAsString(properties, Connection.CONNECTION_TYPE);
        this.ansibleBinPath = PropertyUtil.getPropertyAsString(properties, ANSIBLE_COMMAND, "ansible");
        this.ansiblePlaybookBinPath = PropertyUtil.getPropertyAsString(properties, ANSIBLE_PLAYBOOK_COMMAND, "ansible-playbook");
        // Connect timeout and delay that will be used to wait for SSH connection becomes available on the target
        this.connectTimeout = getPropertyAsTime(properties, CONNECT_TIMEOUT, "5 m");
        this.delay = getPropertyAsTime(properties, DELAY, "2 s");
    }

    private static long getPropertyAsTime(Map<String, Object> properties, String propertyName, String defaultValue) {
        String waitBetweenConnectRetry = PropertyUtil.getPropertyAsString(properties, propertyName, defaultValue);
        return ToscaUtil.convertToSeconds(waitBetweenConnectRetry);
    }

    private String getHomeDirOnControlMachine() {
        try {
            return CommandUtil.evaluate(controlMachineConnection, "echo $HOME");
        } catch (Exception r) {
            log.warn("Unable to retrieve home directory on control machine", r.getMessage());
            return "";
        }
    }

    private Path writeToTempFile(String content) throws IOException {
        Path temporaryKeyFile = Files.createTempFile("", "");
        Files.write(temporaryKeyFile, content.getBytes(StandardCharsets.UTF_8));
        return temporaryKeyFile;
    }

    private void moveKey(Path pemPath) {
        this.remoteKeyFile = this.userDataDir.resolve("_toscaruntime.pem").toString();
        Integer commandStatus = executeCommandOnControlMachine("mv " + this.userDataDir.resolve(pemPath.getFileName()).toString() + " " + this.remoteKeyFile + " && chmod 0400 " + this.remoteKeyFile);
        if (commandStatus == null || commandStatus != 0) {
            throw new UnexpectedException("Unable to move key on the control machine, exit status [" + commandStatus + "]");
        }
    }

    protected Integer executeCommandOnControlMachine(String command) {
        return controlMachineConnection.executeCommand(command);
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

    protected AnsibleCommandBuilder appendConnectionInfo(AnsibleCommandBuilder ansibleCommandBuilder) {
        return ansibleCommandBuilder
                .export("ANSIBLE_HOST_KEY_CHECKING", "False")
                .option("inventory-file", getTargetWithPort())
                .option("user", this.user)
                .option("private-key", this.remoteKeyFile)
                .option("connection", this.connectionType);
    }

    private String wrapCommandWithAnsible(String command) {
        String ansibleCommand = appendConnectionInfo(new AnsibleCommandBuilder(this.ansibleBinPath + " all"))
                .option("module-name", "shell")
                .option("args", String.format("'%s'", command.replace("'", "'\"'\"'")))
                .build();
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
        try {
            return PropertyUtil.toMap(factsRawOutput);
        } catch (PropertyAccessException e) {
            return Collections.emptyMap();
        } finally {
            controlMachineConnection.executeCommand("rm -f " + this.userDataDir.resolve(factPath));
        }
    }

    private AnsibleCommandBuilder wrapPlaybookWithAnsible(String playbookPath, Map<String, Object> variables) {
        AnsibleCommandBuilder ansibleCommandBuilder = appendConnectionInfo(new AnsibleCommandBuilder(this.ansiblePlaybookBinPath));
        if (variables != null && !variables.isEmpty()) {
            try {
                ansibleCommandBuilder = ansibleCommandBuilder.option("extra-vars", "'" + JSONUtil.toString(variables) + "'");
            } catch (JsonProcessingException e) {
                throw new UnexpectedException("Could not serialize environment variables for playbook", e);
            }
        }
        ansibleCommandBuilder.argument(playbookPath);
        return ansibleCommandBuilder;
    }

    private void removeAnsibleConfigFromInput(Map<String, Object> inputsWithoutAnsibleConfig, String entity) {
        String allInstances = PropertyUtil.getPropertyAsString(inputsWithoutAnsibleConfig, entity);
        if (StringUtils.isNotBlank(allInstances)) {
            // Remove reserved input ansible from all other instances input
            Arrays.stream(allInstances.split(",")).map(String::trim).forEach(instance -> inputsWithoutAnsibleConfig.remove(instance + "_ansible"));
        }
    }

    @Override
    public Integer executeRemoteArtifact(String playbookPath, Map<String, Object> variables, OutputHandler outputHandler) {
        Map<String, Object> inputsWithoutAnsibleConfig = new HashMap<>(variables);
        // Reserved keyword ansible
        inputsWithoutAnsibleConfig.remove("ansible");
        removeAnsibleConfigFromInput(inputsWithoutAnsibleConfig, InputConstant.INSTANCES);
        removeAnsibleConfigFromInput(inputsWithoutAnsibleConfig, InputConstant.TARGET_INSTANCES);
        // Build the input to execute the playbook
        AnsibleCommandBuilder ansibleCommandBuilder = wrapPlaybookWithAnsible(this.userDataDir.resolve(playbookPath).toString(), inputsWithoutAnsibleConfig);
        // Reserved keyword for specific ansible options
        Map<String, Object> ansibleConfig = PropertyUtil.getPropertyAsMap(variables, "ansible");
        if (ansibleConfig != null) {
            ansibleCommandBuilder
                    .exports(PropertyUtil.getPropertyAsMap(ansibleConfig, "env_vars"))
                    .options(PropertyUtil.getPropertyAsMap(ansibleConfig, "options"))
                    .flags(PropertyUtil.getPropertyAsList(ansibleConfig, "flags"))
                    .arguments(PropertyUtil.getPropertyAsList(ansibleConfig, "arguments"));
        }
        String ansibleCommand = ansibleCommandBuilder.build();
        log.info("Execute playbook with command [{}]", ansibleCommand);
        Integer commandStatus = controlMachineConnection.executeCommand(ansibleCommand, outputHandler);
        if (commandStatus == null || commandStatus != 0) {
            log.error("Execute playbook [" + ansibleCommand + "] failed with status [" + commandStatus + "]");
        }
        return commandStatus;
    }

    @Override
    public Integer executeArtifact(String playbookContent, Map<String, Object> variables, OutputHandler outputHandler) {
        throw new OperationNotImplementedException("Method not implemented");
    }

    @Override
    public void close() {
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

    public long getConnectTimeout() {
        return connectTimeout;
    }

    public long getDelay() {
        return delay;
    }

    public Integer getPort() {
        return port;
    }

    public String getAnsibleBinPath() {
        return ansibleBinPath;
    }

    public String getTarget() {
        return target;
    }
}
