package com.toscaruntime.ansible.executor;

import com.toscaruntime.ansible.connection.AnsibleConnection;
import com.toscaruntime.ansible.util.AnsibleExecutorUtil;
import com.toscaruntime.artifact.Connection;
import com.toscaruntime.artifact.Executor;
import com.toscaruntime.artifact.SimpleOutputHandler;
import com.toscaruntime.exception.deployment.artifact.ArtifactExecutionException;
import com.toscaruntime.exception.deployment.artifact.ArtifactIOException;
import com.toscaruntime.exception.deployment.artifact.BadExecutorConfigurationException;
import com.toscaruntime.util.ArtifactExecutionUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

public class AnsiblePlaybookExecutor implements Executor {

    private static final Logger log = LoggerFactory.getLogger(AnsiblePlaybookExecutor.class);

    private AnsibleConnection connection;

    private String recipeLocation;

    private String remoteLocation;

    private String tempLocation;

    @Override
    public void initialize(Connection connection, Map<String, Object> properties) {
        if (!(connection instanceof AnsibleConnection)) {
            throw new BadExecutorConfigurationException("Ansible executor only works with ansible connection");
        }
        this.connection = (AnsibleConnection) connection;
        this.recipeLocation = (String) properties.get(Executor.LOCAL_RECIPE_LOCATION_KEY);
        if (StringUtils.isBlank(this.recipeLocation)) {
            throw new BadExecutorConfigurationException("Executor is not configured properly, " + Executor.LOCAL_RECIPE_LOCATION_KEY + " is mandatory");
        }
        this.remoteLocation = (String) properties.getOrDefault(Executor.RECIPE_LOCATION_KEY, "recipe");
        this.tempLocation = (String) properties.getOrDefault(Executor.RECIPE_LOCATION_KEY, "tmp");
        refreshRecipe();
    }

    @Override
    public Map<String, Object> executeArtifact(String nodeId, String operation, String operationArtifactPath, Map<String, Object> inputs, Map<String, String> deploymentArtifacts) {
        log.info("Begin to execute [{}][{}][{}] with env [{}] and deployment artifacts [{}]", nodeId, operation, operationArtifactPath, inputs, deploymentArtifacts);
        String executionToken = UUID.randomUUID().toString();
        String remoteArtifactPath = Paths.get(remoteLocation).resolve(operationArtifactPath).toString();
        Path artifactWrapper;
        try {
            artifactWrapper = AnsibleExecutorUtil.createArtifactWrapper(remoteArtifactPath, tempLocation, connection.getUserDataDir(), executionToken);
        } catch (IOException e) {
            throw new ArtifactIOException("Could not create wrapper for playbook", e);
        }
        connection.upload(artifactWrapper.toString(), tempLocation);
        Map<String, Object> artifactInputs = ArtifactExecutionUtil.processDeploymentArtifacts(deploymentArtifacts, connection.getUserDataDir().resolve(remoteLocation).toString(), "/");
        artifactInputs.putAll(inputs);
        try (SimpleOutputHandler outputHandler = new SimpleOutputHandler()) {
            Integer statusCode = connection.executeRemoteArtifact(Paths.get(tempLocation).resolve(artifactWrapper.getFileName()).toString(), artifactInputs, outputHandler);
            if (statusCode != 0) {
                throw new ArtifactExecutionException(String.format("[%s][%s][%s]", nodeId, operation, operationArtifactPath) + " : Playbook execution failed with exit status " + statusCode);
            } else {
                log.info(String.format("[%s][%s][%s]", nodeId, operation, operationArtifactPath) + " : Playbook execution finished normally");
            }
        } finally {
            try {
                Files.delete(artifactWrapper);
            } catch (IOException ignored) {
            }
        }
        return connection.getFacts(Paths.get(tempLocation).resolve(executionToken).toString());
    }

    @Override
    public void refreshRecipe() {
        this.connection.upload(recipeLocation, remoteLocation);
    }
}
