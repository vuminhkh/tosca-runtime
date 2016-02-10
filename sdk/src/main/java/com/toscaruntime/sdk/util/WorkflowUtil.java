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
     * Check if all dependencies of a node are not found anymore in the waiting queue (for creating, configuring or starting)
     *
     * @param nodeInstance the node instance to check
     * @param waitQueue    the waiting queue
     * @return true if all dependencies is satisfied, false otherwise
     */
    public static boolean areDependenciesSatisfied(Root nodeInstance, Set<Root> waitQueue) {
        for (String dependsOnNode : nodeInstance.getNode().getDependsOnNodes()) {
            if (waitQueue.stream().anyMatch(instance -> instance.getName().equals(dependsOnNode))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if all subjugation of a node are not found anymore in the waiting queue (for stopping, deleting)
     *
     * @param nodeInstance the node instance to check
     * @param waitQueue    the waiting queue
     * @return true if all subjugation is satisfied, false otherwise
     */
    public static boolean areSubjugationSatisfied(Root nodeInstance, Set<Root> waitQueue) {
        for (String dependedByNode : nodeInstance.getNode().getDependedByNodes()) {
            if (waitQueue.stream().anyMatch(instance -> instance.getName().equals(dependedByNode))) {
                return false;
            }
        }
        return true;
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
}
