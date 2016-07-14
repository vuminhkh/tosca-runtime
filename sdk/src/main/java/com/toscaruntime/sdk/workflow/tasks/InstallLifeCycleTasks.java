package com.toscaruntime.sdk.workflow.tasks;

import com.toscaruntime.sdk.workflow.tasks.nodes.ConfigureTask;
import com.toscaruntime.sdk.workflow.tasks.nodes.CreateTask;
import com.toscaruntime.sdk.workflow.tasks.nodes.StartTask;
import tosca.nodes.Root;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InstallLifeCycleTasks extends AbstractLifeCycleTasks {

    private AbstractTask createTask;

    private AbstractTask configureTask;

    private AbstractTask startTask;

    private Root nodeInstance;

    public InstallLifeCycleTasks(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance) {
        this.createTask = new CreateTask(nodeInstances, relationshipInstances, nodeInstance);
        this.configureTask = new ConfigureTask(nodeInstances, relationshipInstances, nodeInstance);
        this.startTask = new StartTask(nodeInstances, relationshipInstances, nodeInstance);
        this.nodeInstance = nodeInstance;
    }

    public InstallLifeCycleTasks(AbstractTask createTask, AbstractTask configureTask, AbstractTask startTask, Root nodeInstance) {
        this.createTask = createTask;
        this.configureTask = configureTask;
        this.startTask = startTask;
        this.nodeInstance = nodeInstance;
    }

    public Root getNodeInstance() {
        return nodeInstance;
    }

    public AbstractTask getCreateTask() {
        return createTask;
    }

    public AbstractTask getConfigureTask() {
        return configureTask;
    }

    public AbstractTask getStartTask() {
        return startTask;
    }

    public List<AbstractTask> getTasks() {
        return Arrays.asList(
                getCreateTask(),
                getConfigureTask(),
                getStartTask()
        );
    }
}
