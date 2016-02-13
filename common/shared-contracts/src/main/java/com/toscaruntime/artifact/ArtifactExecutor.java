package com.toscaruntime.artifact;

import java.util.Map;

/**
 * Basic contract for a tosca runtime artifact executor
 *
 * @author Minh Khang VU
 */
public interface ArtifactExecutor {

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

    /**
     * Execute a command on the remote host.
     *
     * @param operationName name of the operation
     * @param command       the command it-self
     * @param env           environment variable
     * @return outputs of the operation
     * @throws Exception
     */
    Map<String, String> executeCommand(String operationName, String command, Map<String, String> env) throws Exception;
}
