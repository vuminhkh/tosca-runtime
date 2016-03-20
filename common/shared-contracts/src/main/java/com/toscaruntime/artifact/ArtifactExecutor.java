package com.toscaruntime.artifact;

import java.nio.file.Path;
import java.util.Map;

/**
 * Basic contract for a tosca runtime artifact executor
 *
 * @author Minh Khang VU
 */
public interface ArtifactExecutor {

    String REMOTE_TEMP_DIR = "/tmp/";

    /**
     * Must be called to initialize the executor
     */
    void initialize();

    /**
     * Execute implementation artifact on the remote host (the artifact has already been uploaded on the host)
     *
     * @param operationName      name of the operation
     * @param localArtifactPath  local path
     * @param remoteArtifactPath path to the artifact on the remote host
     * @param env                environment variable
     * @return outputs of the operation
     */
    Map<String, String> executeArtifact(String operationName, Path localArtifactPath, String remoteArtifactPath, Map<String, String> env);
}
