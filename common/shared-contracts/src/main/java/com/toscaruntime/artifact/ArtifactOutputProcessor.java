package com.toscaruntime.artifact;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class ArtifactOutputProcessor {

    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("^(\\S+)=(.+)$");

    private String statusCodeToken;

    private String environmentVariablesToken;

    protected AtomicReference<Integer> statusCode = new AtomicReference<>();

    protected Map<String, String> capturedEnvVars = new HashMap<>();

    public ArtifactOutputProcessor(String statusCodeToken, String environmentVariablesToken) {
        this.statusCodeToken = statusCodeToken;
        this.environmentVariablesToken = environmentVariablesToken;
    }

    public String processNewLine(String line) {
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
            return null;
        } else if (line.startsWith(statusCodeToken)) {
            if (statusCode.get() == null) {
                // Only update status code with the one of the wrapper script first
                statusCode.set(Integer.parseInt(line.substring(statusCodeToken.length())));
            }
            return null;
        } else {
            return line;
        }
    }

    public Map<String, String> getCapturedEnvVars() {
        return capturedEnvVars;
    }

    public Integer getStatusCode() {
        return statusCode.get();
    }
}
