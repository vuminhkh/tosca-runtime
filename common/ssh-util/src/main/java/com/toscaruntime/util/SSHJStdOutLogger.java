package com.toscaruntime.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;

public class SSHJStdOutLogger implements Callable<Void> {

    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("^(\\S+)=(.+)$");

    private String operationName;

    private String scriptName;

    private Logger logger;

    private boolean endOfOutputDetected = false;

    private String endOfOutputToken;

    private Map<String, String> capturedEnvVars = new HashMap<>();

    private InputStream scriptOutput;

    public SSHJStdOutLogger(String operationName, String scriptName, Logger logger, String endOfOutputToken, InputStream scriptOutput) {
        this.operationName = operationName;
        this.scriptName = scriptName;
        this.logger = logger;
        this.endOfOutputToken = endOfOutputToken;
        this.scriptOutput = scriptOutput;
    }

    @Override
    public Void call() throws Exception {
        try (BufferedReader inputReader = new BufferedReader(new InputStreamReader(scriptOutput))) {
            String line = inputReader.readLine();
            while (line != null) {
                if (endOfOutputDetected) {
                    Matcher matcher = ENV_VAR_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        capturedEnvVars.put(matcher.group(1), matcher.group(2));
                    }
                } else if (line.equals(endOfOutputToken)) {
                    endOfOutputDetected = true;
                } else {
                    logger.info("[{}][{}][stdout] {}", operationName, scriptName, line);
                }
                line = inputReader.readLine();
            }
        }
        return null;
    }

    public Map<String, String> getCapturedEnvVars() {
        return capturedEnvVars;
    }

}
