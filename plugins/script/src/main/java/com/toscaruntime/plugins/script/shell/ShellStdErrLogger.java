package com.toscaruntime.plugins.script.shell;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.Callable;

import org.slf4j.Logger;

public class ShellStdErrLogger implements Callable<Void> {

    private String operationName;

    private String scriptName;

    private Logger logger;

    private InputStream scriptOutput;

    public ShellStdErrLogger(String operationName, String scriptName, Logger logger, InputStream scriptOutput) {
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
                this.logger.info("[{}][{}][stderr] {}", operationName, scriptName, line);
                line = inputReader.readLine();
            }
        }
        return null;
    }
}
