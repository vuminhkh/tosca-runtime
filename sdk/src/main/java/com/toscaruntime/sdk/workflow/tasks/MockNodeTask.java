package com.toscaruntime.sdk.workflow.tasks;

import java.util.Map;
import java.util.Set;

import com.toscaruntime.sdk.workflow.tasks.nodes.AbstractNodeTask;
import com.toscaruntime.util.CodeGeneratorUtil;

import tosca.nodes.Root;

public class MockNodeTask extends AbstractNodeTask {

    private String interfaceName;

    private String operationName;

    public MockNodeTask(String interfaceName, String operationName, Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance) {
        super(nodeInstances, relationshipInstances, nodeInstance);
        this.interfaceName = interfaceName;
        this.operationName = operationName;
    }

    @Override
    protected void doRun() {
        // Mock task do not run
    }

    @Override
    public String getInterfaceName() {
        return interfaceName;
    }

    @Override
    public String getOperationName() {
        return operationName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        MockNodeTask that = (MockNodeTask) o;

        if (!getInterfaceName().equals(that.getInterfaceName())) return false;
        return getOperationName().equals(that.getOperationName());

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + getInterfaceName().hashCode();
        result = 31 * result + getOperationName().hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Mock " + CodeGeneratorUtil.getGeneratedMethodName(this.interfaceName, this.operationName) + " for " + nodeInstance;
    }
}
