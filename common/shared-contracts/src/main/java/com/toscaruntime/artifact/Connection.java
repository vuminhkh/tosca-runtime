package com.toscaruntime.artifact;

import java.io.Closeable;
import java.util.Map;

public interface Connection extends Closeable {

    String USER = "user";

    String PORT = "port";

    String KEY_PATH = "key_path";

    String KEY_CONTENT = "key_content";

    String TARGET = "target";

    String CONNECTION_TYPE = "connection_type";

    String SHELL = "shell";

    /**
     * Called at the beginning to initialize the connection
     *
     * @param properties Properties of artifact executor plugin to initialize the connection
     */
    void initialize(Map<String, Object> properties);

    /**
     * Upload file / folder from localPath to remoteDirectory.
     * If localPath is a file then the file will be copied under the remote directory with the same name.
     * If localPath is a directory then all of its content will be copied under the remote directory.
     * The remote directory will be created if not existing, and will be overwritten if already exists.
     *
     * @param localPath       the local file path
     * @param remoteDirectory the remote directory where the file will be copied to
     */
    void upload(String localPath, String remoteDirectory);

    /**
     * Execute a script on the connected host
     *
     * @param artifactContent script's content
     * @param variables       script's variables
     * @param outputHandler   handler for script's output
     * @return status code
     */
    Integer executeArtifact(String artifactContent, Map<String, Object> variables, OutputHandler outputHandler);

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
    Integer executeRemoteArtifact(String artifactPath, Map<String, Object> variables, OutputHandler outputHandler);

    /**
     * Close the connection and free all occupied resources
     */
    @Override
    void close();
}
