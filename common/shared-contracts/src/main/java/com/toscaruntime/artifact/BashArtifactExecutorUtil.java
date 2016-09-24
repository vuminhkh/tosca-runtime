package com.toscaruntime.artifact;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Common utilities for artifact execution
 *
 * @author Minh Khang VU
 */
public class BashArtifactExecutorUtil {

    private static void captureExitStatusAndEnvironmentVariables(PrintWriter commandWriter, String statusCodeToken, String environmentVariablesToken) {
        // Check exit code
        commandWriter.println("_toscaruntime_rc=$?");
        // Mark the beginning of environment variables
        commandWriter.println("echo " + environmentVariablesToken + "`printenv | sed -e ':a' -e 'N' -e '$!ba' -e 's/\\n/" + environmentVariablesToken + "/g'`");
        // Print env in order to be able to capture environment variables
        // Mark the beginning of status code
        commandWriter.println("echo " + statusCodeToken + "$_toscaruntime_rc");
    }

    public static String createArtifactWrapper(String artifactPath, String statusCodeToken, String environmentVariablesToken, String sheBang) {
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

    public static List<String> getSetEnvCommands(Map<String, String> env) {
        // Set envs
        if (env != null) {
            return env.entrySet().stream().filter(envEntry -> envEntry.getValue() != null).map(
                    envEntry -> "export " + envEntry.getKey() + "='" + envEntry.getValue() + "'"
            ).collect(Collectors.toList());
        } else {
            return new ArrayList<>();
        }
    }

    public static String readSheBang(Path script) throws IOException {
        return readSheBang(new InputStreamReader(Files.newInputStream(script), "UTF-8"));
    }

    public static String readSheBang(Reader script) throws IOException {
        try (BufferedReader reader = new BufferedReader(script)) {
            String line = reader.readLine();
            while (line != null) {
                line = line.trim();
                if (line.length() > 0) {
                    if (line.startsWith("#!")) {
                        return line;
                    } else {
                        // Not found return a default value
                        return "#!/bin/sh";
                    }
                }
                line = reader.readLine();
            }
            // Not found return a default value
            return "#!/bin/sh";
        }
    }

    public static String readInterpreterCommand(Reader script) throws IOException {
        String shebang = readSheBang(script);
        return shebang.substring(2);
    }
}
