package com.toscaruntime.plugins.script.shell;

import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.Callable;

public class ShellStdOutLogger extends ArtifactOutputProcessor implements Callable<Void> {

    private String node;

    private String operationName;

    private String scriptName;

    private Logger logger;

    private InputStream scriptOutput;

    public ShellStdOutLogger(String node, String operationName, String scriptName, Logger logger, String statusToken, String environmentVariablesToken, InputStream scriptOutput) {
        super(statusToken, environmentVariablesToken);
        this.node = node;
        this.operationName = operationName;
        this.scriptName = scriptName;
        this.logger = logger;
        this.scriptOutput = scriptOutput;
    }

    @Override
    public Void call() throws Exception {
        try (BufferedReader inputReader = new BufferedReader(new InputStreamReader(scriptOutput))) {
            String line = inputReader.readLine();
            while (line != null) {
                try {
                    String processedLine = processNewLine(line);
                    if (processedLine != null) {
                        logger.info("[{}[{}][{}][stdout] {}", node, operationName, scriptName, processedLine);
                    }
                } catch (Exception e) {
                    logger.warn("Could not process correctly new output line", e);
                }
                line = inputReader.readLine();
            }
        }
        return null;
    }
}
