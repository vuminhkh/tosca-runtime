package com.toscaruntime.sdk.workflow.tasks;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.toscaruntime.sdk.workflow.WorkflowExecution;

public abstract class AbstractTask implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(AbstractTask.class);

    protected Set<AbstractTask> dependsOnTasks = new HashSet<>();

    protected Set<AbstractTask> dependedByTasks = new HashSet<>();

    protected WorkflowExecution workflowExecution;

    private void notifyTaskCompletion() {
        try {
            workflowExecution.getLock().lock();
            dependedByTasks.stream().forEach(dependedByInstance -> dependedByInstance.onDependencyCompletion(this));
            // The dependency is notified about the completion of the task before the workflow execution
            // This way the workflow execution can validate that there is no cyclic dependencies
            workflowExecution.onTaskCompletion(this);
        } finally {
            workflowExecution.getLock().unlock();
        }
    }

    private void notifyTaskError(Throwable e) {
        workflowExecution.onTaskFailure(this, e);
    }

    private void onDependencyCompletion(AbstractTask dependency) {
        try {
            workflowExecution.getLock().lock();
            if (!dependsOnTasks.remove(dependency)) {
                log.error("Notified completion of an unknown dependency {} for task {}", dependency, toString());
            }
        } finally {
            workflowExecution.getLock().unlock();
        }
    }

    public void dependsOn(AbstractTask... others) {
        this.dependsOnTasks.addAll(Arrays.asList(others));
        for (AbstractTask other : others) {
            other.dependedByTasks.add(this);
        }
    }

    protected abstract void doRun() throws Throwable;

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

    public WorkflowExecution getWorkflowExecution() {
        return workflowExecution;
    }

    public void setWorkflowExecution(WorkflowExecution workflowExecution) {
        this.workflowExecution = workflowExecution;
    }

    public Set<AbstractTask> getDependsOnTasks() {
        return dependsOnTasks;
    }

    public void setDependsOnTasks(Set<AbstractTask> dependsOnTasks) {
        this.dependsOnTasks = dependsOnTasks;
    }

    public Set<AbstractTask> getDependedByTasks() {
        return dependedByTasks;
    }
}
