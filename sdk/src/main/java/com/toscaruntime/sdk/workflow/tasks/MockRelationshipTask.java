package com.toscaruntime.sdk.workflow.tasks;

import java.util.Map;
import java.util.Set;

import com.toscaruntime.sdk.workflow.tasks.relationships.AbstractRelationshipTask;
import com.toscaruntime.util.CodeGeneratorUtil;

import tosca.nodes.Root;

/**
 * This task is used in case of scaling where we have connections to the outside or from the outside, we need to mock the lifecycle of the external source or target that they have already been created/started.
 *
 * @author Minh Khang VU
 */
public class MockRelationshipTask extends AbstractRelationshipTask {

    private String interfaceName;

    private String operationName;

    public MockRelationshipTask(String interfaceName, String operationName, Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, tosca.relationships.Root relationshipInstance) {
        super(nodeInstances, relationshipInstances, relationshipInstance);
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

        MockRelationshipTask that = (MockRelationshipTask) o;

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
        return "Mock " + CodeGeneratorUtil.getGeneratedMethodName(this.interfaceName, this.operationName) + " for " + relationshipInstance;
    }
}
