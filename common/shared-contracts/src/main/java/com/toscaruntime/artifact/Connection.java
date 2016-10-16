package com.toscaruntime.artifact;

import java.io.Closeable;
import java.util.Map;

public interface Connection extends Closeable {

    String USER = "user";

    String PORT = "port";

    String KEY_PATH = "key_path";

    String KEY_CONTENT = "key_content";

    String TARGET = "target";
    
    /**
     * Called at the beginning to initialize the connection
     *
     * @param properties Properties of artifact executor plugin to initialize the connection
     */
    void initialize(Map<String, Object> properties);

    /**
     * Upload file / folder from localPath to remotePath
     *
     * @param localPath  the local file path
     * @param remotePath the remote target path
     */
    void upload(String localPath, String remotePath);

    /**
     * Execute a script on the connected host
     *
     * @param artifactContent script's content
     * @param variables       script's variables
     * @param outputHandler   handler for script's output
     * @return status code
     */
    Integer executeArtifact(String artifactContent, Map<String, String> variables, OutputHandler outputHandler);

    /**
     * Execute a command on the connected host, just log out the standard output and the error output
     *
     * @param command the command to execute
     * @return status code
     */
    Integer executeCommand(String command);

    /**
     * Execute a command on the connected host
     *
     * @param command       the command to execute
     * @param outputHandler handler for command's output
     * @return status code
     */
    Integer executeCommand(String command, OutputHandler outputHandler);

    /**
     * Execute a remote artifact on the connected host (the artifact is on the remote host)
     *
     * @param artifactPath  script's path
     * @param variables     script's variables
     * @param outputHandler handler for script's output
     * @return status code
     */
    Integer executeRemoteArtifact(String artifactPath, Map<String, String> variables, OutputHandler outputHandler);

    /**
     * Close the connection and free all occupied resources
     */
    @Override
    void close();
}
