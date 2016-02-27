package com.toscaruntime.sdk.util;

import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.toscaruntime.sdk.model.AbstractRuntimeType;
import com.toscaruntime.sdk.workflow.tasks.AbstractTask;
import com.toscaruntime.sdk.workflow.tasks.MockTask;

import tosca.nodes.Root;

public class WorkflowUtil {


    private static void refreshAttributes(Map<String, Root> nodeInstances,
                                          Set<tosca.relationships.Root> relationshipInstances) {
        nodeInstances.values().forEach(Root::refreshAttributes);
        relationshipInstances.forEach(tosca.relationships.Root::refreshAttributes);
    }

    public static void refreshDeploymentState(Map<String, tosca.nodes.Root> nodeInstances,
                                              Set<tosca.relationships.Root> relationshipInstances,
                                              AbstractRuntimeType instance,
                                              String newState,
                                              boolean refreshAttributes) {
        if (StringUtils.isNotBlank(newState)) {
            instance.setState(newState);
        }
        if (refreshAttributes) {
            refreshAttributes(nodeInstances, relationshipInstances);
        }
    }

    /**
     * Create a mock by copying attributes from the given task
     *
     * @param taskName name of the task
     * @param copyFrom the task to copy from
     * @return mock task
     */
    public static MockTask mockTask(String taskName, AbstractTask copyFrom) {
        return new MockTask(taskName, copyFrom.getNodeInstances(), copyFrom.getRelationshipInstances(), copyFrom.getNodeInstance(), copyFrom.getTaskExecutor(), copyFrom.getWorkflowExecution());
    }

    public static void changeRelationshipState(
            tosca.relationships.Root relationshipInstance,
            Map<String, Root> nodeInstances,
            Set<tosca.relationships.Root> relationshipInstances,
            String fromState,
            String toState) {
        // This will prevent concurrent state changes
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (relationshipInstance) {
            if (relationshipInstance.getState().equals(fromState)) {
                refreshDeploymentState(nodeInstances, relationshipInstances, relationshipInstance, toState, true);
            } else {
                refreshDeploymentState(nodeInstances, relationshipInstances, relationshipInstance, fromState, true);
            }
        }
    }
}
