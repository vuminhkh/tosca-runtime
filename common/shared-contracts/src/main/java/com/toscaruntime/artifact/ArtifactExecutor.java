package com.toscaruntime.artifact;

import java.util.Map;

/**
 * Basic contract for a tosca runtime artifact executor
 *
 * @author Minh Khang VU
 */
public interface ArtifactExecutor {

    /**
     * Must be called to initialize the executor
     *
     * @throws Exception
     */
    void initialize() throws Exception;

    /**
     * Execute implementation artifact on the remote host
     *
     * @param operationName name of the operation
     * @param artifactPath  path to the artifact on the remote host
     * @param env           environment variable
     * @return outputs of the operation
     * @throws Exception
     */
    Map<String, String> executeArtifact(String operationName, String artifactPath, Map<String, String> env) throws Exception;
}
