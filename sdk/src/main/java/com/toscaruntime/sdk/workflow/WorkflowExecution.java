package com.toscaruntime.sdk.workflow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.toscaruntime.exception.InvalidWorkflowException;
import com.toscaruntime.sdk.workflow.tasks.AbstractTask;

public class WorkflowExecution {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecution.class);

    private Set<AbstractTask> tasksLeft = new HashSet<>();

    private ReentrantLock lock = new ReentrantLock();

    private Condition finishedCondition = lock.newCondition();

    private Throwable error;

    private List<WorkflowExecutionListener> listeners = new ArrayList<>();

    public boolean isFinished() {
        try {
            lock.lock();
            return tasksLeft.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    public void onTaskFailure(Throwable t) {
        try {
            lock.lock();
            error = t;
            finishedCondition.signalAll();
            listeners.forEach(listener -> listener.onFailure(error));
        } finally {
            lock.unlock();
        }
    }

    public void onTaskCompletion(AbstractTask completedTask) {
        try {
            lock.lock();
            if (!tasksLeft.remove(completedTask)) {
                log.warn("Notified of completion of unknown task {}", completedTask);
            } else if (tasksLeft.isEmpty()) {
                // No task left
                finishedCondition.signalAll();
                listeners.forEach(this::notifyCompletionToListener);
            } else {
                // Check to see if the deployment can continue
                boolean cyclicDependency = true;
                for (AbstractTask task : tasksLeft) {
                    if (task.canRun()) {
                        cyclicDependency = false;
                        break;
                    }
                }
                if (cyclicDependency) {
                    log.error("Cyclic dependencies detected: \n {}", toString());
                    onTaskFailure(new InvalidWorkflowException("Cyclic dependencies detected, cannot process workflow execution anymore"));
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void notifyCompletionToListener(WorkflowExecutionListener listener) {
        try {
            listener.onFinish();
        } catch (Throwable e) {
            log.error("Listener throw unexpected error", e);
            notifyErrorToListener(listener, e);
        }
    }

    private void notifyErrorToListener(WorkflowExecutionListener listener, Throwable e) {
        try {
            listener.onFailure(e);
        } catch (Throwable fe) {
            log.error("Listener throw unexpected error", fe);
        }
    }

    public void addListener(WorkflowExecutionListener listener) {
        try {
            lock.lock();
            if (error != null) {
                notifyErrorToListener(listener, error);
            } else if (tasksLeft.isEmpty()) {
                notifyCompletionToListener(listener);
            } else {
                listeners.add(listener);
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean waitForCompletion(long timeout, TimeUnit unit) throws Throwable {
        try {
            lock.lock();
            if (tasksLeft.isEmpty()) {
                return true;
            } else {
                boolean signaled = finishedCondition.await(timeout, unit);
                if (!signaled) {
                    return false;
                } else if (error != null) {
                    throw error;
                } else {
                    return true;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void addTasks(List<AbstractTask> tasks) {
        tasksLeft.addAll(tasks);
    }

    @Override
    public String toString() {
        try {
            lock.lock();
            if (isFinished()) {
                return "Workflow execution is finished";
            } else {
                StringBuilder buffer = new StringBuilder("Workflow execution has ").append(tasksLeft.size()).append(" tasks left:\n");
                for (AbstractTask task : tasksLeft) {
                    buffer.append("\t- ").append(task).append(" depends on [").append(task.getDependsOnTasks()).append("]\n");
                }
                buffer.append("Inter-dependent tasks:\n");
                for (AbstractTask task : tasksLeft) {
                    Set<AbstractTask> interDependencies = task.getDependsOnTasks().stream().filter(dependencyTask -> !dependencyTask.getNodeInstance().equals(task.getNodeInstance())).collect(Collectors.toSet());
                    if (!interDependencies.isEmpty()) {
                        buffer.append("\t- ").append(task).append(" depends on [").append(interDependencies).append("]\n");
                    }
                }
                return buffer.toString();
            }
        } finally {
            lock.unlock();
        }
    }

}
