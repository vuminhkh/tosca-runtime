package com.toscaruntime.artifact;

import java.util.Map;

/**
 * Basic contract for a tosca runtime artifact executor
 *
 * @author Minh Khang VU
 */
public interface Executor {

    /**
     * Property key to get the remote location of the recipe once uploaded to the target VM
     */
    String RECIPE_LOCATION_KEY = "recipe_location";

    /**
     * Property key to get the location of the recipe on the micro manager
     */
    String LOCAL_RECIPE_LOCATION_KEY = "local_recipe_location";

    /**
     * Called at the beginning to initialize the executor
     *
     * @param connection the connection to execute the artifact
     * @param properties properties of the executor to initialize
     */
    void initialize(Connection connection, Map<String, Object> properties);

    /**
     * Execute implementation artifact on the remote host
     *
     * @param nodeId                the node on which the artifact is executed
     * @param operation             the operation's name
     * @param operationArtifactPath path to the operation's artifact inside the recipe
     * @param inputs                inputs of the operation
     * @param deploymentArtifacts   deployment artifacts associated to the operation
     * @return outputs of the operation
     */
    Map<String, String> executeArtifact(String nodeId, String operation, String operationArtifactPath, Map<String, Object> inputs, Map<String, String> deploymentArtifacts);

    /**
     * This method is called when the recipe needs to be refreshed
     */
    void refreshRecipe();
}
