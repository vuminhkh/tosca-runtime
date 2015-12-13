package com.toscaruntime.util;

import org.slf4j.Logger;

/**
 * Simple stream log error output to logger
 *
 * @author Minh Khang VU
 */
public class SSHStdErrLoggerOutputStream extends SSHOutputStream {

    private String scriptName;

    private Logger logger;

    public SSHStdErrLoggerOutputStream(String operationName, String scriptName, Logger logger) {
        this.scriptName = scriptName;
        this.logger = logger;
        this.operationName = operationName;
    }

    @Override
    protected void handleNewLine(String newLine) {
        this.logger.info("[{}][{}][stderr] {}", operationName, scriptName, newLine);
    }
}
