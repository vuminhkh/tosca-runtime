package com.toscaruntime.sdk.workflow.tasks;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.toscaruntime.sdk.workflow.WorkflowExecution;

import tosca.nodes.Root;

public abstract class AbstractTask implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(AbstractTask.class);

    protected Map<String, Root> nodeInstances;

    protected Set<tosca.relationships.Root> relationshipInstances;

    protected Root nodeInstance;

    protected Set<AbstractTask> dependsOnTasks = new HashSet<>();

    protected Set<AbstractTask> dependedByTasks = new HashSet<>();

    private ExecutorService taskExecutor;

    private WorkflowExecution workflowExecution;

    public AbstractTask(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, ExecutorService taskExecutor, WorkflowExecution workflowExecution) {
        this.nodeInstances = nodeInstances;
        this.relationshipInstances = relationshipInstances;
        this.nodeInstance = nodeInstance;
        this.taskExecutor = taskExecutor;
        this.workflowExecution = workflowExecution;
    }

    public synchronized void notifyTaskCompletion() {
        dependedByTasks.stream().forEach(dependedByInstance -> dependedByInstance.onDependencyCompletion(this));
        workflowExecution.onTaskCompletion(this);
    }

    public synchronized void notifyTaskError(Throwable e) {
        workflowExecution.onTaskFailure(e);
    }

    public synchronized void onDependencyCompletion(AbstractTask dependency) {
        if (!dependsOnTasks.remove(dependency)) {
            log.error("Notified completion of an unknown dependency {} for task {}", dependency, this);
        } else if (dependsOnTasks.isEmpty()) {
            taskExecutor.submit(this);
        }
    }

    public void dependsOn(AbstractTask... others) {
        this.dependsOnTasks.addAll(Arrays.asList(others));
        for (AbstractTask other : others) {
            other.dependedByTasks.add(this);
        }
    }

    protected abstract void doRun();

    @Override
    public void run() {
        try {
            doRun();
            notifyTaskCompletion();
        } catch (Throwable e) {
            notifyTaskError(e);
        }
    }

    public boolean canRun() {
        return dependsOnTasks.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AbstractTask that = (AbstractTask) o;

        return nodeInstance != null ? nodeInstance.equals(that.nodeInstance) : that.nodeInstance == null;

    }

    @Override
    public int hashCode() {
        return nodeInstance != null ? nodeInstance.hashCode() : 0;
    }

    public Map<String, Root> getNodeInstances() {
        return nodeInstances;
    }

    public Set<tosca.relationships.Root> getRelationshipInstances() {
        return relationshipInstances;
    }

    public Root getNodeInstance() {
        return nodeInstance;
    }

    public ExecutorService getTaskExecutor() {
        return taskExecutor;
    }

    public WorkflowExecution getWorkflowExecution() {
        return workflowExecution;
    }
}
