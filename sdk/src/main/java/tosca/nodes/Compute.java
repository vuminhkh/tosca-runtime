package tosca.nodes;

import com.toscaruntime.artifact.ArtifactExecutor;
import com.toscaruntime.exception.deployment.artifact.ArtifactExecutorNotFoundException;

import java.util.HashMap;
import java.util.Map;

/**
 * A compute is a node that can execute an artifact locally on it-self
 */
public abstract class Compute extends Root {

    /**
     * Mapping artifact type to artifact executor
     */
    private Map<String, ArtifactExecutor> executors = new HashMap<>();

    protected void registerArtifactExecutor(String artifactType, ArtifactExecutor artifactExecutor) {
        executors.put(artifactType, artifactExecutor);
    }

    protected ArtifactExecutor getArtifactExecutor(String artifactType) {
        ArtifactExecutor artifactExecutor = executors.get(artifactType);
        if (artifactExecutor == null) {
            throw new ArtifactExecutorNotFoundException("Artifact " + artifactType + " is not supported by the provider");
        }
        return artifactExecutor;
    }

    /**
     * Upload the recipe's content to the compute, this method is used to force a compute to refresh its recipe
     */
    public abstract void uploadRecipe();

    /**
     * Run operation on the compute instance
     *
     * @param nodeId                id of the node that the operation will be run for
     * @param operationArtifactPath the relative path to the script in the recipe
     * @param inputs                environment variables for the operation
     * @param deploymentArtifacts   deployment artifacts that might be used by the operation
     */
    public abstract Map<String, String> execute(String nodeId, String operationArtifactPath, Map<String, Object> inputs, Map<String, String> deploymentArtifacts);

}
