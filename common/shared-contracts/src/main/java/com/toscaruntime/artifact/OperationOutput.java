package com.toscaruntime.artifact;

import java.util.Map;

public class OperationOutput {

    private Integer statusCode;

    private Map<String, Object> outputs;

    public OperationOutput(Integer statusCode, Map<String, Object> outputs) {
        this.statusCode = statusCode;
        this.outputs = outputs;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public Map<String, Object> getOutputs() {
        return outputs;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public void setOutputs(Map<String, Object> outputs) {
        this.outputs = outputs;
    }
}
