package com.toscaruntime.sdk.workflow.tasks;

import com.toscaruntime.sdk.workflow.tasks.nodes.DeleteTask;
import com.toscaruntime.sdk.workflow.tasks.nodes.StopTask;
import tosca.nodes.Root;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UninstallLifeCycleTasks extends AbstractLifeCycleTasks {

    private AbstractTask stopTask;

    private AbstractTask deleteTask;

    private Root nodeInstance;

    public UninstallLifeCycleTasks(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance) {
        this.stopTask = new StopTask(nodeInstances, relationshipInstances, nodeInstance);
        this.deleteTask = new DeleteTask(nodeInstances, relationshipInstances, nodeInstance);
        this.nodeInstance = nodeInstance;
    }

    public UninstallLifeCycleTasks(AbstractTask stopTask, AbstractTask deleteTask, Root nodeInstance) {
        this.stopTask = stopTask;
        this.deleteTask = deleteTask;
        this.nodeInstance = nodeInstance;
    }

    public Root getNodeInstance() {
        return nodeInstance;
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
