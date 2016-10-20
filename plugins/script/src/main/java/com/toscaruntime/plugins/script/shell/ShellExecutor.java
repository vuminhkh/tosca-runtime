package com.toscaruntime.plugins.script.shell;

import com.toscaruntime.artifact.Connection;
import com.toscaruntime.artifact.ConnectionUtil;
import com.toscaruntime.artifact.Executor;
import com.toscaruntime.artifact.OperationOutput;
import com.toscaruntime.artifact.OutputHandler;
import com.toscaruntime.exception.InterruptedByUserException;
import com.toscaruntime.exception.deployment.artifact.ArtifactConnectException;
import com.toscaruntime.exception.deployment.artifact.ArtifactExecutionException;
import com.toscaruntime.exception.deployment.artifact.ArtifactIOException;
import com.toscaruntime.exception.deployment.artifact.BadExecutorConfigurationException;
import com.toscaruntime.util.ArtifactExecutionUtil;
import com.toscaruntime.util.FailSafeUtil;
import com.toscaruntime.util.PropertyUtil;
import com.toscaruntime.util.ToscaUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class ShellExecutor implements Executor {

    private static final Logger log = LoggerFactory.getLogger(ShellExecutor.class);

    private Connection connection;

    private String recipeLocation;

    private String remoteLocation;

    private int artifactExecutionRetry;

    private long waitBetweenArtifactExecutionRetry;

    @Override
    public void initialize(Connection connection, Map<String, Object> properties) {
        this.connection = connection;
        this.recipeLocation = (String) properties.get(Executor.LOCAL_RECIPE_LOCATION_KEY);
        if (StringUtils.isBlank(this.recipeLocation)) {
            throw new BadExecutorConfigurationException("Executor is not configured properly, " + Executor.RECIPE_LOCATION_KEY + " is mandatory");
        }
        this.remoteLocation = (String) properties.getOrDefault(Executor.RECIPE_LOCATION_KEY, "/tmp/recipe");
        this.artifactExecutionRetry = getArtifactExecutionRetry(properties);
        this.waitBetweenArtifactExecutionRetry = getWaitBetweenArtifactExecutionRetry(properties);
        long waitBeforeArtifactExecution = getWaitBeforeArtifactExecution(properties);
        try {
            Thread.sleep(waitBeforeArtifactExecution);
        } catch (InterruptedException e) {
            throw new InterruptedByUserException("Interrupted", e);
        }
        refreshRecipe();
    }

    private static int getArtifactExecutionRetry(Map<String, Object> properties) {
        return Integer.parseInt(PropertyUtil.getPropertyAsString(properties, "configuration.artifact_execution_retry", "1"));
    }

    private static long getWaitBetweenArtifactExecutionRetry(Map<String, Object> properties) {
        String waitBetweenArtifactExecutionRetry = PropertyUtil.getPropertyAsString(properties, "configuration.wait_between_artifact_execution_retry", "10 s");
        return ToscaUtil.convertToSeconds(waitBetweenArtifactExecutionRetry);
    }

    private long getWaitBeforeArtifactExecution(Map<String, Object> properties) {
        String waitBeforeArtifactExecution = PropertyUtil.getPropertyAsString(properties, "configuration.wait_before_artifact_execution", "10 s");
        return ToscaUtil.convertToSeconds(waitBeforeArtifactExecution);
    }

    @Override
    public Map<String, Object> executeArtifact(String nodeId, String operation, String operationArtifactPath, Map<String, Object> inputs, Map<String, String> deploymentArtifacts) {
        return FailSafeUtil.doActionWithRetryNoCheckedException(
                () -> doExecuteArtifact(nodeId, operation, operationArtifactPath, inputs, deploymentArtifacts),
                operationArtifactPath,
                artifactExecutionRetry,
                waitBetweenArtifactExecutionRetry,
                TimeUnit.SECONDS,
                ArtifactExecutionException.class,
                ArtifactConnectException.class
        );
    }

    private Map<String, Object> doExecuteArtifact(String nodeId, String operation, String operationArtifactPath, Map<String, Object> inputs, Map<String, String> deploymentArtifacts) {
        log.info("Begin to execute [{}][{}][{}] with env [{}] and deployment artifacts [{}]", nodeId, operation, operationArtifactPath, inputs, deploymentArtifacts);
        String remoteArtifactPath = Paths.get(remoteLocation).resolve(operationArtifactPath).toString();
        Path localPath = Paths.get(recipeLocation).resolve(operationArtifactPath);
        String statusCodeToken = UUID.randomUUID().toString();
        String environmentVariablesToken = UUID.randomUUID().toString();
        try (OutputHandler outputHandler = new ShellOutputHandler(statusCodeToken, environmentVariablesToken, nodeId + "/" + operation, localPath.getFileName().toString())) {
            String sheBang = ConnectionUtil.readSheBang(localPath);
            String artifactWrapper = ShellExecutorUtil.createArtifactWrapper(remoteArtifactPath, statusCodeToken, environmentVariablesToken, sheBang);
            // Set env
            Map<String, Object> artifactInputs = ArtifactExecutionUtil.processDeploymentArtifacts(deploymentArtifacts, remoteLocation, "/");
            artifactInputs.putAll(inputs);
            Integer statusCode = connection.executeArtifact(artifactWrapper, ArtifactExecutionUtil.processInputs(artifactInputs), outputHandler);
            OperationOutput operationOutput;
            try {
                operationOutput = outputHandler.getOperationOutput();
            } catch (ExecutionException | InterruptedException e) {
                throw new ArtifactExecutionException(String.format("[%s][%s][%s]", nodeId, operation, operationArtifactPath) + " : Script failed to executed", e);
            }
            if (operationOutput.getStatusCode() != null) {
                statusCode = operationOutput.getStatusCode();
            }
            if (statusCode != 0) {
                throw new ArtifactExecutionException(String.format("[%s][%s][%s]", nodeId, operation, operationArtifactPath) + " : Script failed to executed with exit status " + statusCode);
            } else {
                log.info(String.format("[%s][%s][%s]", nodeId, operation, operationArtifactPath) + " : Script execution finished normally");
            }
            return operationOutput.getOutputs();
        } catch (IOException e) {
            throw new ArtifactIOException(String.format("[%s][%s][%s]", nodeId, operation, operationArtifactPath) + " :  Error happened while trying to generate wrapper script", e);
        }
    }

    @Override
    public void refreshRecipe() {
        this.connection.upload(recipeLocation, remoteLocation);
    }
}
