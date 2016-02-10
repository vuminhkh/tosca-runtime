package com.toscaruntime.sdk.workflow.tasks;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import com.toscaruntime.sdk.util.WorkflowUtil;
import com.toscaruntime.sdk.workflow.WorkflowExecution;
import com.toscaruntime.sdk.workflow.tasks.nodes.ConfigureTask;
import com.toscaruntime.sdk.workflow.tasks.nodes.CreateTask;
import com.toscaruntime.sdk.workflow.tasks.nodes.StartTask;
import com.toscaruntime.sdk.workflow.tasks.relationships.AddSourceTask;
import com.toscaruntime.sdk.workflow.tasks.relationships.AddTargetTask;
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

    private void mockAllTask(AbstractTask mockTask) {
        this.createTask = WorkflowUtil.mockTask("Create Task", mockTask);
        this.preConfigureSourceTask = WorkflowUtil.mockTask("Pre Configure Source Task", mockTask);
        this.preConfigureTargetTask = WorkflowUtil.mockTask("Pre Configure Target Task", mockTask);
        this.configureTask = WorkflowUtil.mockTask("Configure Task", mockTask);
        this.postConfigureSourceTask = WorkflowUtil.mockTask("Post Configure Source Task", mockTask);
        this.postConfigureTargetTask = WorkflowUtil.mockTask("Post Configure Target Task", mockTask);
        this.startTask = WorkflowUtil.mockTask("Start Task", mockTask);
        this.addSourceTask = WorkflowUtil.mockTask("Add Source Task", mockTask);
        this.addTargetTask = WorkflowUtil.mockTask("Add Target Task", mockTask);
    }

    public InstallLifeCycleTasks(MockTask mockTask) {
        mockAllTask(mockTask);
        initTasksDependencies();
    }

    public InstallLifeCycleTasks(AddSourceTask addSourceTask) {
        mockAllTask(addSourceTask);
        this.addSourceTask = addSourceTask;
        initTasksDependencies();
    }

    public InstallLifeCycleTasks(AddTargetTask addTargetTask) {
        mockAllTask(addTargetTask);
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
