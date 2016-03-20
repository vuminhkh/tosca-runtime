package com.toscaruntime.sdk.workflow.tasks;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.toscaruntime.sdk.workflow.WorkflowExecution;
import com.toscaruntime.sdk.workflow.tasks.nodes.DeleteTask;
import com.toscaruntime.sdk.workflow.tasks.nodes.StopTask;

import tosca.nodes.Root;

public class UninstallLifeCycleTasks extends AbstractLifeCycleTasks {

    private AbstractTask stopTask;

    private AbstractTask deleteTask;

    public UninstallLifeCycleTasks(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, WorkflowExecution workflowExecution) {
        this.stopTask = new StopTask(nodeInstances, relationshipInstances, nodeInstance, workflowExecution);
        this.deleteTask = new DeleteTask(nodeInstances, relationshipInstances, nodeInstance, workflowExecution);
    }

    public UninstallLifeCycleTasks(AbstractTask stopTask, AbstractTask deleteTask) {
        this.stopTask = stopTask;
        this.deleteTask = deleteTask;
    }

    public AbstractTask getStopTask() {
        return stopTask;
    }

    public AbstractTask getDeleteTask() {
        return deleteTask;
    }

    public List<AbstractTask> getTasks() {
        return Arrays.asList(
                getStopTask(),
                getDeleteTask()
        );
    }
}
