package com.toscaruntime.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;

/**
 * An output stream which take a logger and log everything with it. It also captures environment variables
 *
 * @author Minh Khang VU
 */
public class SSHStdOutLoggerOutputStream extends SSHOutputStream {

    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("^(\\S+)=(.+)$");

    private String scriptName;

    private Logger logger;

    private boolean endOfOutputDetected = false;

    private String endOfOutputToken;

    private Map<String, String> capturedEnvVars = new HashMap<>();

    public SSHStdOutLoggerOutputStream(String operationName, String scriptName, Logger logger, String endOfOutputToken) {
        this.operationName = operationName;
        this.scriptName = scriptName;
        this.logger = logger;
        this.endOfOutputToken = endOfOutputToken;
    }

    @Override
    protected void handleNewLine(String newLine) {
        if (this.endOfOutputToken.equals(newLine)) {
            endOfOutputDetected = true;
        } else {
            if (endOfOutputDetected) {
                Matcher matcher = ENV_VAR_PATTERN.matcher(newLine);
                if (matcher.matches()) {
                    capturedEnvVars.put(matcher.group(1), matcher.group(2));
                }
            } else {
                this.logger.info("[{}][{}][stdout] {}", operationName, scriptName, newLine);
            }
        }
    }

    public Map<String, String> getCapturedEnvVars() {
        return capturedEnvVars;
    }
}
