package com.toscaruntime.artifact;

import org.slf4j.Logger;

public class SimpleCommandOutputHandler extends AbstractOutputHandler {

    private String commandName;

    private Logger log;

    public SimpleCommandOutputHandler(String command, Logger log) {
        this.commandName = ConnectionUtil.truncateArtifactName(command, 30);
        this.log = log;
    }

    protected void onData(String source, String line) {
        this.log.info("[{}][{}]: {}", commandName, source, line);
    }
}
