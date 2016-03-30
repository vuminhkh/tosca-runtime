package com.toscaruntime.artifact;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
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
public class ArtifactExecutorUtil {

    public static void createArtifactExecutor(PrintWriter commandWriter, String remotePath, String sheBang, String statusCodeToken, String environmentVariablesToken) {
        // Remove #! from the shebang to get the path to shell binary
        String shellPath = sheBang.substring(2);
        commandWriter.println("chmod +x " + remotePath);
        commandWriter.println(shellPath + " < " + remotePath);
        // Try to execute commands to capture exit status and environment variables
        captureExitStatusAndEnvironmentVariables(commandWriter, statusCodeToken, environmentVariablesToken);
        commandWriter.println("exit");
        commandWriter.flush();
    }

    private static void captureExitStatusAndEnvironmentVariables(PrintWriter commandWriter, String statusCodeToken, String environmentVariablesToken) {
        // Check exit code
        commandWriter.println("_toscaruntime_rc=$?");
        // Mark the beginning of environment variables
        commandWriter.println("echo " + environmentVariablesToken + "`printenv | sed -e ':a' -e 'N' -e '$!ba' -e 's/\\n/" + environmentVariablesToken + "/g'`");
        // Print env in order to be able to capture environment variables
        // Mark the beginning of status code
        commandWriter.println("echo " + statusCodeToken + "$_toscaruntime_rc");
    }

    public static Path createArtifactWrapper(String artifactPath, Map<String, String> env, String statusCodeToken, String environmentVariablesToken, String sheBang, boolean elevatePrivilege) throws IOException {
        Path localGeneratedScriptPath = Files.createTempFile("", ".sh");
        try (PrintWriter commandWriter = new PrintWriter(new OutputStreamWriter(new BufferedOutputStream(Files.newOutputStream(localGeneratedScriptPath)), StandardCharsets.UTF_8))) {
            commandWriter.println(sheBang);
            if (elevatePrivilege) {
                commandWriter.println("sudo -s");
            }
            // Set env
            List<String> setEnvCommands = ArtifactExecutorUtil.getSetEnvCommands(env);
            setEnvCommands.stream().forEach(commandWriter::println);
            // Make executable
            commandWriter.println("chmod +x " + artifactPath);
            // Launch the script
            commandWriter.println(". " + artifactPath);
            // Try to execute commands to capture exit status and environment variables
            captureExitStatusAndEnvironmentVariables(commandWriter, statusCodeToken, environmentVariablesToken);
            // Exit the ssh session
            if (elevatePrivilege) {
                commandWriter.println("exit");
            }
            commandWriter.println("exit");
            // If elevatePrivilege is enabled then perform sudo -s
            commandWriter.flush();
        }
        return localGeneratedScriptPath;
    }

    private static List<String> getSetEnvCommands(Map<String, String> env) {
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
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(script), "UTF-8"))) {
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
}
