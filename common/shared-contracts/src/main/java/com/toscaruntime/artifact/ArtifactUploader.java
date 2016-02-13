package com.toscaruntime.artifact;

/**
 * Basic contract for a tosca runtime artifact uploader
 *
 * @author Minh Khang VU
 */
public interface ArtifactUploader {

    /**
     * Upload from localPath to remotePath
     *
     * @param localPath  the local file path
     * @param remotePath the remote target path
     * @throws Exception
     */
    void upload(String localPath, String remotePath) throws Exception;
}
