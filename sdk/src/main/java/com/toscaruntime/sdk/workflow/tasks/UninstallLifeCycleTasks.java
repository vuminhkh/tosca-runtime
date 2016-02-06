package com.toscaruntime.sdk.workflow.tasks;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import com.toscaruntime.sdk.workflow.WorkflowExecution;
import com.toscaruntime.sdk.workflow.tasks.nodes.DeleteTask;
import com.toscaruntime.sdk.workflow.tasks.nodes.StopTask;
import com.toscaruntime.sdk.workflow.tasks.relationships.MockTask;
import com.toscaruntime.sdk.workflow.tasks.relationships.RemoveSourceTask;
import com.toscaruntime.sdk.workflow.tasks.relationships.RemoveTargetTask;

import tosca.nodes.Root;

public class UninstallLifeCycleTasks extends AbstractLifeCycleTasks {

    private AbstractTask removeSourceTask;

    private AbstractTask removeTargetTask;

    private AbstractTask stopTask;

    private AbstractTask deleteTask;

    private void initDependencies() {
        // Dependencies between tasks of the same node instance are declared here
        // Remove source and remove target are executed before stop
        stopTask.dependsOn(removeSourceTask, removeTargetTask);
        // Delete is executed after stop
        deleteTask.dependsOn(stopTask);
    }

    public UninstallLifeCycleTasks(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, ExecutorService executorService, WorkflowExecution workflowExecution) {
        this.stopTask = new StopTask(nodeInstances, relationshipInstances, nodeInstance, executorService, workflowExecution);
        this.deleteTask = new DeleteTask(nodeInstances, relationshipInstances, nodeInstance, executorService, workflowExecution);
        this.removeSourceTask = new RemoveSourceTask(nodeInstances, relationshipInstances, nodeInstance, executorService, workflowExecution);
        this.removeTargetTask = new RemoveTargetTask(nodeInstances, relationshipInstances, nodeInstance, executorService, workflowExecution);
        initDependencies();
    }

    public UninstallLifeCycleTasks(RemoveSourceTask removeSourceTask) {
        this.removeSourceTask = removeSourceTask;
        this.removeTargetTask = new MockTask(removeSourceTask);
        this.stopTask = new MockTask(removeSourceTask);
        this.deleteTask = new MockTask(removeSourceTask);
        initDependencies();
    }

    public UninstallLifeCycleTasks(RemoveTargetTask removeTargetTask) {
        this.removeSourceTask = new MockTask(removeTargetTask);
        this.removeTargetTask = removeTargetTask;
        this.stopTask = new MockTask(removeTargetTask);
        this.deleteTask = new MockTask(removeTargetTask);
        initDependencies();
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
        return Arrays.asList(getRemoveSourceTask(),
                getRemoveTargetTask(),
                getStopTask(),
                getDeleteTask()
        );
    }
}
