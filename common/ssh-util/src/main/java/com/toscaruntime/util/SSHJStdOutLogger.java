package com.toscaruntime.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;

public class SSHJStdOutLogger implements Callable<Void> {

    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("^(\\S+)=(.+)$");

    private String operationName;

    private String scriptName;

    private Logger logger;

    private String statusCodeToken;

    private String environmentVariablesToken;

    private AtomicReference<Integer> statusCode = new AtomicReference<>();

    private Map<String, String> capturedEnvVars = new HashMap<>();

    private InputStream scriptOutput;

    public SSHJStdOutLogger(String operationName, String scriptName, Logger logger, String endOfScriptToken, String endOfOutputToken, InputStream scriptOutput) {
        this.operationName = operationName;
        this.scriptName = scriptName;
        this.logger = logger;
        this.statusCodeToken = endOfScriptToken;
        this.environmentVariablesToken = endOfOutputToken;
        this.scriptOutput = scriptOutput;
    }

    @Override
    public Void call() throws Exception {
        try (BufferedReader inputReader = new BufferedReader(new InputStreamReader(scriptOutput))) {
            String line = inputReader.readLine();
            while (line != null) {
                if (line.startsWith(environmentVariablesToken)) {
                    String[] allEnvVarsWithValues = line.split(environmentVariablesToken);
                    for (String envVarWithValue : allEnvVarsWithValues) {
                        if (statusCode.get() == null) {
                            // Only update the env vars with the ones of the wrapper script first
                            Matcher matcher = ENV_VAR_PATTERN.matcher(envVarWithValue);
                            if (matcher.matches()) {
                                capturedEnvVars.put(matcher.group(1), matcher.group(2));
                            }
                        }
                    }
                } else if (line.startsWith(statusCodeToken)) {
                    if (statusCode.get() == null) {
                        // Only update status code with the one of the wrapper script first
                        try {
                            statusCode.set(Integer.parseInt(line.substring(statusCodeToken.length())));
                        } catch (Exception e) {
                            logger.warn("For script " + scriptName + " of operation " + operationName + ", could not parse status code", e);
                        }
                    }
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

    public Integer getStatusCode() {
        return statusCode.get();
    }
}
