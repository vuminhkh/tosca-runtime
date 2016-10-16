package com.toscaruntime.plugins.script.shell;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Common utilities for artifact execution
 *
 * @author Minh Khang VU
 */
public class ShellExecutorUtil {

    private static void captureExitStatusAndEnvironmentVariables(PrintWriter commandWriter, String statusCodeToken, String environmentVariablesToken) {
        // Check exit code
        commandWriter.println("_toscaruntime_rc=$?");
        // Mark the beginning of environment variables
        commandWriter.println("echo " + environmentVariablesToken + "`printenv | sed -e ':a' -e 'N' -e '$!ba' -e 's/\\n/" + environmentVariablesToken + "/g'`");
        // Print env in order to be able to capture environment variables
        // Mark the beginning of status code
        commandWriter.println("echo " + statusCodeToken + "$_toscaruntime_rc");
    }

    static String createArtifactWrapper(String artifactPath, String statusCodeToken, String environmentVariablesToken, String sheBang) {
        StringWriter rawWriter = new StringWriter();
        try (PrintWriter commandWriter = new PrintWriter(rawWriter)) {
            commandWriter.println(sheBang);
            // Make executable
            commandWriter.println("chmod +x " + artifactPath);
            // Launch the script
            commandWriter.println(". " + artifactPath);
            // Try to execute commands to capture exit status and environment variables
            captureExitStatusAndEnvironmentVariables(commandWriter, statusCodeToken, environmentVariablesToken);
            // Exit the ssh session
            commandWriter.println("exit");
            // If elevatePrivilege is enabled then perform sudo -s
            commandWriter.flush();
        }
        return rawWriter.toString();
    }

}
