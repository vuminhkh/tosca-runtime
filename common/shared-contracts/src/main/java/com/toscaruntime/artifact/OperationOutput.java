package com.toscaruntime.artifact;

import java.util.Map;

public class OperationOutput {

    private Integer statusCode;

    private Map<String, String> outputs;

    public OperationOutput(Integer statusCode, Map<String, String> outputs) {
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
