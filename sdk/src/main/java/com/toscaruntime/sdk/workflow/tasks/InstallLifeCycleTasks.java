package com.toscaruntime.sdk.workflow.tasks;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import com.toscaruntime.sdk.workflow.WorkflowExecution;
import com.toscaruntime.sdk.workflow.tasks.nodes.ConfigureTask;
import com.toscaruntime.sdk.workflow.tasks.nodes.CreateTask;
import com.toscaruntime.sdk.workflow.tasks.nodes.StartTask;
import com.toscaruntime.sdk.workflow.tasks.relationships.AddSourceTask;
import com.toscaruntime.sdk.workflow.tasks.relationships.AddTargetTask;
import com.toscaruntime.sdk.workflow.tasks.relationships.MockTask;
import com.toscaruntime.sdk.workflow.tasks.relationships.PostConfigureSourceTask;
import com.toscaruntime.sdk.workflow.tasks.relationships.PostConfigureTargetTask;
import com.toscaruntime.sdk.workflow.tasks.relationships.PreConfigureSourceTask;
import com.toscaruntime.sdk.workflow.tasks.relationships.PreConfigureTargetTask;

import tosca.nodes.Root;

public class InstallLifeCycleTasks extends AbstractLifeCycleTasks {

    private AbstractTask createTask;

    private AbstractTask preConfigureSourceTask;

    private AbstractTask preConfigureTargetTask;

    private AbstractTask configureTask;

    private AbstractTask postConfigureSourceTask;

    private AbstractTask postConfigureTargetTask;

    private AbstractTask startTask;

    private AbstractTask addSourceTask;

    private AbstractTask addTargetTask;

    private void initTasksDependencies() {
        // Dependencies between tasks of the same node instance are declared here
        // Pre configure source and target of the instance are executed after create
        preConfigureSourceTask.dependsOn(createTask);
        preConfigureTargetTask.dependsOn(createTask);
        // Configure is executed after pre configure source and target
        configureTask.dependsOn(preConfigureSourceTask, preConfigureTargetTask);
        // Post configure source and target are executed after configure
        postConfigureSourceTask.dependsOn(configureTask);
        postConfigureTargetTask.dependsOn(configureTask);
        // Start is executed after post configure source and target
        startTask.dependsOn(postConfigureSourceTask, postConfigureTargetTask);
        // Add source and target are executed after start
        addSourceTask.dependsOn(startTask);
        addTargetTask.dependsOn(startTask);
    }

    public InstallLifeCycleTasks(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, ExecutorService executorService, WorkflowExecution workflowExecution) {
        this.createTask = new CreateTask(nodeInstances, relationshipInstances, nodeInstance, executorService, workflowExecution);
        this.configureTask = new ConfigureTask(nodeInstances, relationshipInstances, nodeInstance, executorService, workflowExecution);
        this.startTask = new StartTask(nodeInstances, relationshipInstances, nodeInstance, executorService, workflowExecution);
        this.preConfigureSourceTask = new PreConfigureSourceTask(nodeInstances, relationshipInstances, nodeInstance, executorService, workflowExecution);
        this.preConfigureTargetTask = new PreConfigureTargetTask(nodeInstances, relationshipInstances, nodeInstance, executorService, workflowExecution);
        this.postConfigureSourceTask = new PostConfigureSourceTask(nodeInstances, relationshipInstances, nodeInstance, executorService, workflowExecution);
        this.postConfigureTargetTask = new PostConfigureTargetTask(nodeInstances, relationshipInstances, nodeInstance, executorService, workflowExecution);
        this.addSourceTask = new AddSourceTask(nodeInstances, relationshipInstances, nodeInstance, executorService, workflowExecution);
        this.addTargetTask = new AddTargetTask(nodeInstances, relationshipInstances, nodeInstance, executorService, workflowExecution);
        initTasksDependencies();
    }

    public InstallLifeCycleTasks(AddSourceTask addSourceTask) {
        this.createTask = new MockTask(addSourceTask);
        this.preConfigureSourceTask = new MockTask(addSourceTask);
        this.preConfigureTargetTask = new MockTask(addSourceTask);
        this.configureTask = new MockTask(addSourceTask);
        this.postConfigureSourceTask = new MockTask(addSourceTask);
        this.postConfigureTargetTask = new MockTask(addSourceTask);
        this.startTask = new MockTask(addSourceTask);
        this.addSourceTask = addSourceTask;
        this.addTargetTask = new MockTask(addSourceTask);
        initTasksDependencies();
    }

    public InstallLifeCycleTasks(AddTargetTask addTargetTask) {
        this.createTask = new MockTask(addTargetTask);
        this.preConfigureSourceTask = new MockTask(addTargetTask);
        this.preConfigureTargetTask = new MockTask(addTargetTask);
        this.configureTask = new MockTask(addTargetTask);
        this.postConfigureSourceTask = new MockTask(addTargetTask);
        this.postConfigureTargetTask = new MockTask(addTargetTask);
        this.startTask = new MockTask(addTargetTask);
        this.addSourceTask = new MockTask(addTargetTask);
        this.addTargetTask = addTargetTask;
        initTasksDependencies();
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

    public AbstractTask getPreConfigureSourceTask() {
        return preConfigureSourceTask;
    }

    public AbstractTask getPreConfigureTargetTask() {
        return preConfigureTargetTask;
    }

    public AbstractTask getPostConfigureSourceTask() {
        return postConfigureSourceTask;
    }

    public AbstractTask getPostConfigureTargetTask() {
        return postConfigureTargetTask;
    }

    public AbstractTask getAddSourceTask() {
        return addSourceTask;
    }

    public AbstractTask getAddTargetTask() {
        return addTargetTask;
    }

    public List<AbstractTask> getTasks() {
        return Arrays.asList(
                getCreateTask(),
                getPreConfigureSourceTask(),
                getPreConfigureTargetTask(),
                getConfigureTask(),
                getPostConfigureSourceTask(),
                getPostConfigureTargetTask(),
                getStartTask(),
                getAddSourceTask(),
                getAddTargetTask()
        );
    }
}
