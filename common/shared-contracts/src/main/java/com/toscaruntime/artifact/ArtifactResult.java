package com.toscaruntime.artifact;

import java.util.Map;

public class ArtifactResult {

    private Integer statusCode;

    private Map<String, String> outputs;

    public ArtifactResult(Integer statusCode, Map<String, String> outputs) {
        this.statusCode = statusCode;
        this.outputs = outputs;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public Map<String, String> getOutputs() {
        return outputs;
    }
}
