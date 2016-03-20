package com.toscaruntime.util;

import com.toscaruntime.artifact.ArtifactOutputProcessor;

class DockerStdOutLogger extends ArtifactOutputProcessor {

    DockerStdOutLogger(String statusCodeToken, String environmentVariablesToken) {
        super(statusCodeToken, environmentVariablesToken);
    }
}
