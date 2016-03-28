package com.toscaruntime.deployment;

import java.util.Map;

/**
 * This represents a running execution that was loaded at initial load
 *
 * @author Minh Khang VU
 */
public class RunningExecutionDTO {

    private String workflowId;

    private Map<String, Object> inputs;

    public RunningExecutionDTO(String workflowId, Map<String, Object> inputs) {
        this.workflowId = workflowId;
        this.inputs = inputs;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public Map<String, Object> getInputs() {
        return inputs;
    }
}
