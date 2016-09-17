package com.toscaruntime.artifact;

import java.io.Closeable;
import java.util.Map;

public interface Connection extends Closeable {

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
     * Execute a command on the connected host
     *
     * @param command the command to execute
     */
    Integer executeCommand(String command);

    /**
     * Execute a script on the connected host
     *
     * @param scriptContent script's content
     * @param variables     script's variables
     * @param outputHandler handler for script's output
     * @return status code
     */
    Integer executeScript(String scriptContent, Map<String, String> variables, OutputHandler outputHandler);
}
