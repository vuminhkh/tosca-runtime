package com.toscaruntime.sdk.workflow.tasks;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.toscaruntime.sdk.util.WorkflowUtil;
import com.toscaruntime.sdk.workflow.WorkflowExecution;
import com.toscaruntime.sdk.workflow.tasks.nodes.DeleteTask;
import com.toscaruntime.sdk.workflow.tasks.nodes.StopTask;
import com.toscaruntime.sdk.workflow.tasks.relationships.RemoveSourceTask;
import com.toscaruntime.sdk.workflow.tasks.relationships.RemoveTargetTask;

import tosca.nodes.Root;

public class UninstallLifeCycleTasks extends AbstractLifeCycleTasks {

    private AbstractTask removeSourceTask;

    private AbstractTask removeTargetTask;

    private AbstractTask stopTask;

    private AbstractTask deleteTask;

    public UninstallLifeCycleTasks(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, WorkflowExecution workflowExecution) {
        this.stopTask = new StopTask(nodeInstances, relationshipInstances, nodeInstance, workflowExecution);
        this.deleteTask = new DeleteTask(nodeInstances, relationshipInstances, nodeInstance, workflowExecution);
        this.removeSourceTask = new RemoveSourceTask(nodeInstances, relationshipInstances, nodeInstance, workflowExecution);
        this.removeTargetTask = new RemoveTargetTask(nodeInstances, relationshipInstances, nodeInstance, workflowExecution);
    }

    private void mockAllTask(AbstractTask mockTask) {
        this.removeSourceTask = WorkflowUtil.mockTask("Remove Source Task", mockTask);
        this.removeTargetTask = WorkflowUtil.mockTask("Remove Target Task", mockTask);
        this.stopTask = WorkflowUtil.mockTask("Stop Task", mockTask);
        this.deleteTask = WorkflowUtil.mockTask("Delete Task", mockTask);
    }

    public UninstallLifeCycleTasks(RemoveSourceTask removeSourceTask) {
        mockAllTask(removeSourceTask);
        this.removeSourceTask = removeSourceTask;
    }

    public UninstallLifeCycleTasks(RemoveTargetTask removeTargetTask) {
        mockAllTask(removeTargetTask);
        this.removeTargetTask = removeTargetTask;
    }

    public UninstallLifeCycleTasks(MockTask mockTask) {
        mockAllTask(mockTask);
    }

    public AbstractTask getRemoveSourceTask() {
        return removeSourceTask;
    }

    public AbstractTask getRemoveTargetTask() {
        return removeTargetTask;
    }

    public AbstractTask getStopTask() {
        return stopTask;
    }

    public AbstractTask getDeleteTask() {
        return deleteTask;
    }

    public List<AbstractTask> getTasks() {
        return Arrays.asList(
                getRemoveSourceTask(),
                getRemoveTargetTask(),
                getStopTask(),
                getDeleteTask()
        );
    }
}
