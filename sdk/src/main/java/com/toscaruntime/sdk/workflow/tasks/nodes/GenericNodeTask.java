package com.toscaruntime.sdk.workflow.tasks.nodes;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import com.toscaruntime.exception.deployment.workflow.InvalidWorkflowCommandException;
import com.toscaruntime.sdk.util.WorkflowUtil;
import com.toscaruntime.util.CodeGeneratorUtil;

import tosca.nodes.Root;

/**
 * Generic tasks to be executed on a node
 *
 * @author Minh Khang VU
 */
public class GenericNodeTask extends AbstractNodeTask {

    private String interfaceName;

    private String operationName;

    public GenericNodeTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, String interfaceName, String operationName) {
        super(nodeInstances, relationshipInstances, nodeInstance);
        this.interfaceName = interfaceName;
        this.operationName = operationName;
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
    protected void doRun() throws Throwable {
        try {
            Method method = nodeInstance.getClass().getMethod(CodeGeneratorUtil.getGeneratedMethodName(interfaceName, operationName));
            method.invoke(nodeInstance);
            WorkflowUtil.refreshAttributes(nodeInstances, relationshipInstances);
        } catch (IllegalAccessException | NoSuchMethodException e) {
            throw new InvalidWorkflowCommandException(e.getMessage(), e);
        } catch (InvocationTargetException e) {
            if (e.getCause() != null) {
                throw e.getCause();
            } else {
                throw e;
            }
        }
    }
}
