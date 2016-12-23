package com.toscaruntime.artifact;

import com.toscaruntime.exception.deployment.artifact.ArtifactExecutionException;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class CommandUtil {

    /**
     * Evaluate a command with the given connection to retrieve its output.
     *
     * @param connection the connection
     * @param command    the command to evaluate
     * @return the output of the command
     * @throws com.toscaruntime.exception.deployment.artifact.ArtifactExecutionException if the command exits with error status code
     */
    public static String evaluate(Connection connection, String command) {
        String marker = UUID.randomUUID().toString();
        final AtomicReference<StringBuilder> valueHolder = new AtomicReference<>(new StringBuilder());
        try (AbstractOutputHandler outputHandler = new AbstractOutputHandler() {

            private AtomicBoolean beginCapture = new AtomicBoolean(false);

            @Override
            protected void onData(String source, String line) {
                if (line.equals(marker)) {
                    // toggle
                    beginCapture.set(!beginCapture.get());
                } else {
                    if (beginCapture.get()) {
                        valueHolder.get().append(line);
                    }
                }
            }
        }) {
            Integer status = connection.executeCommand("echo " + marker + " && " + command + " && echo " + marker, outputHandler);
            if (status == null || status != 0) {
                throw new ArtifactExecutionException("Command failed to execute [" + command + "] with output [" + valueHolder.get() + "]");
            }
            return valueHolder.get().toString();
        }
    }
}
