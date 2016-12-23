package com.toscaruntime.artifact;

import org.slf4j.Logger;

public class SimpleArtifactOutputHandler extends AbstractOutputHandler {

    private String nodeId;

    private String operationName;

    private String scriptName;

    private Logger log;

    public SimpleArtifactOutputHandler(String nodeId, String operationName, String scriptName, Logger log) {
        this.nodeId = nodeId;
        this.operationName = operationName;
        this.scriptName = ConnectionUtil.truncateArtifactName(scriptName, 30);
        this.log = log;
    }

    @Override
    protected void onData(String source, String line) {
        log.info("[{}][{}][{}][{}]: {}", nodeId, operationName, scriptName, source, line);
    }
}
