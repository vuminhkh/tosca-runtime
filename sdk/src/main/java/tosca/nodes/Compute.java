package tosca.nodes;

import com.toscaruntime.artifact.Connection;
import com.toscaruntime.artifact.Executor;
import com.toscaruntime.artifact.ExecutorConfiguration;
import com.toscaruntime.exception.deployment.artifact.ArtifactNotSupportedException;
import com.toscaruntime.exception.deployment.artifact.BadExecutorConfigurationException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A compute is a node that can execute an artifact locally on it-self
 */
public abstract class Compute extends Root {

    /**
     * Mapping artifact type to artifact executor
     */
    private List<ExecutorConfiguration> connectionRegistry = new ArrayList<>();

    private Map<String, Executor> executorCache = new HashMap<>();

    /**
     * A connection must be stateless and reusable as a singleton
     */
    private Map<Class<? extends Connection>, Connection> connectionCache = new HashMap<>();

    public void registerExecutor(ExecutorConfiguration executorConfiguration) {
        connectionRegistry.add(executorConfiguration);
    }

    protected synchronized Executor getArtifactExecutor(String artifactType) {
        Executor executor = executorCache.get(artifactType);
        if (executor == null) {
            Class<? extends Executor> executorType = config.getArtifactExecutorRegistry().get(artifactType);
            if (executorType == null) {
                throw new ArtifactNotSupportedException("Artifact [" + artifactType + "] is not supported");
            }
            try {
                executor = executorType.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new BadExecutorConfigurationException("Executor [" + executorType + "] is not instantiable", e);
            }
            Optional<ExecutorConfiguration> optionalConfiguration = connectionRegistry.stream().filter(executorConfiguration -> executorConfiguration.getExecutorType().equals(executorType)).findFirst();
            if (!optionalConfiguration.isPresent()) {
                throw new ArtifactNotSupportedException("Artifact [" + artifactType + "] is not supported, as no connection is found on this compute [" + this.getClass().getName() + "]");
            }
            ExecutorConfiguration executorConfiguration = optionalConfiguration.get();
            Connection connection = connectionCache.get(executorConfiguration.getConnectionType());
            try {
                if (connection == null) {
                    connection = executorConfiguration.getConnectionType().newInstance();
                    connection.initialize(executorConfiguration.getProperties());
                    connectionCache.put(executorConfiguration.getConnectionType(), connection);
                }
            } catch (InstantiationException | IllegalAccessException e) {
                throw new BadExecutorConfigurationException("Connection [" + executorConfiguration.getConnectionType() + "] is not instantiable", e);
            }
            executor.initialize(connection, executorConfiguration.getProperties());
            executorCache.put(artifactType, executor);
        }
        return executor;
    }

    /**
     * Run operation on the compute instance
     *
     * @param nodeId                id of the node that the operation will be run for
     * @param operation             name of the operation
     * @param operationArtifactPath the relative path to the script in the recipe
     * @param artifactType          type of the artifact
     * @param inputs                environment variables for the operation
     * @param deploymentArtifacts   deployment artifacts that might be used by the operation
     */
    public Map<String, String> execute(String nodeId, String operation, String operationArtifactPath, String artifactType, Map<String, Object> inputs, Map<String, String> deploymentArtifacts) {
        return getArtifactExecutor(artifactType).executeArtifact(nodeId, operation, operationArtifactPath, inputs, deploymentArtifacts);
    }

    /**
     * Upload the recipe's content to the compute, this method is used to force a compute to refresh its recipe
     */
    public void uploadRecipe() {
        executorCache.values().forEach(Executor::refreshRecipe);
    }
}
