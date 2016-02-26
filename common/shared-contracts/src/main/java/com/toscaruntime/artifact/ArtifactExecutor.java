package com.toscaruntime.artifact;

import java.nio.file.Path;
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
     * Execute implementation artifact on the remote host (the artifact has already been uploaded on the host)
     *
     * @param operationName      name of the operation
     * @param localArtifactPath  local path
     * @param remoteArtifactPath path to the artifact on the remote host
     * @param env                environment variable
     * @return outputs of the operation
     * @throws Exception
     */
    Map<String, String> executeArtifact(String operationName, Path localArtifactPath, String remoteArtifactPath, Map<String, String> env) throws Exception;
}
