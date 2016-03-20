package com.toscaruntime.artifact;

/**
 * Basic contract for a tosca runtime artifact uploader
 *
 * @author Minh Khang VU
 */
public interface ArtifactUploader {

    /**
     * Must be called to initialize the executor
     */
    void initialize();

    /**
     * Upload from localPath to remotePath
     *
     * @param localPath  the local file path
     * @param remotePath the remote target path
     */
    void upload(String localPath, String remotePath);
}
