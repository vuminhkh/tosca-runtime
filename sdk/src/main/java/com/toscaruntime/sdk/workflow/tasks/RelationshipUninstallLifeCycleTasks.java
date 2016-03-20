package com.toscaruntime.sdk.workflow.tasks;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.toscaruntime.sdk.workflow.WorkflowExecution;
import com.toscaruntime.sdk.workflow.tasks.relationships.RemoveSourceTask;
import com.toscaruntime.sdk.workflow.tasks.relationships.RemoveTargetTask;

import tosca.nodes.Root;

public class RelationshipUninstallLifeCycleTasks extends AbstractLifeCycleTasks {

    private AbstractTask removeSourceTask;

    private AbstractTask removeTargetTask;

    public RelationshipUninstallLifeCycleTasks(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, tosca.relationships.Root relationshipInstance, WorkflowExecution workflowExecution) {
        this.removeSourceTask = new RemoveSourceTask(nodeInstances, relationshipInstances, relationshipInstance, workflowExecution);
        this.removeTargetTask = new RemoveTargetTask(nodeInstances, relationshipInstances, relationshipInstance, workflowExecution);
    }

    public RelationshipUninstallLifeCycleTasks(AbstractTask removeSourceTask, AbstractTask removeTargetTask) {
        this.removeSourceTask = removeSourceTask;
        this.removeTargetTask = removeTargetTask;
    }

    public AbstractTask getRemoveSourceTask() {
        return removeSourceTask;
    }

    public AbstractTask getRemoveTargetTask() {
        return removeTargetTask;
    }

    @Override
    public List<AbstractTask> getTasks() {
        return Arrays.asList(
                getRemoveSourceTask(),
                getRemoveTargetTask()
        );
    }
}
