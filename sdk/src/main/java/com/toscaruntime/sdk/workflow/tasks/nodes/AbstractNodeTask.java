package com.toscaruntime.sdk.workflow.tasks.nodes;

import com.toscaruntime.sdk.workflow.tasks.AbstractOperationTask;
import tosca.nodes.Root;

import java.util.Map;
import java.util.Set;

public abstract class AbstractNodeTask extends AbstractOperationTask {

    protected Root nodeInstance;

    public AbstractNodeTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance) {
        super(nodeInstances, relationshipInstances);
        this.nodeInstance = nodeInstance;
    }

    public Root getNodeInstance() {
        return nodeInstance;
    }

    @Override
    protected void doRun() throws Throwable {
        nodeInstance.executePluginsHooksBeforeOperation(getInterfaceName(), getOperationName());
        doRunNodeOperation();
        nodeInstance.executePluginsHooksAfterOperation(getInterfaceName(), getOperationName());
    }

    protected abstract void doRunNodeOperation() throws Throwable;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AbstractNodeTask that = (AbstractNodeTask) o;

        return nodeInstance.equals(that.nodeInstance);

    }

    @Override
    public int hashCode() {
        return nodeInstance.hashCode();
    }
}
