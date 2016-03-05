package com.toscaruntime.sdk.workflow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.toscaruntime.exception.deployment.workflow.InvalidWorkflowException;
import com.toscaruntime.sdk.workflow.tasks.AbstractTask;

public class WorkflowExecution {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecution.class);

    private Set<AbstractTask> tasksLeft = new HashSet<>();

    private Set<AbstractTask> tasksInError = new HashSet<>();

    private ReentrantLock lock = new ReentrantLock();

    private Condition finishedCondition = lock.newCondition();

    private List<Throwable> errors = new ArrayList<>();

    private List<WorkflowExecutionListener> listeners = new ArrayList<>();

    private ExecutorService executorService;

    public WorkflowExecution(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public boolean isFinished() {
        try {
            lock.lock();
            return tasksLeft.isEmpty() && tasksInError.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    public void onTaskFailure(AbstractTask errorTask, Throwable t) {
        try {
            lock.lock();
            errors.add(t);
            if (errorTask != null) {
                if (!tasksLeft.remove(errorTask)) {
                    log.warn("Notified of errors of unknown task {}", errorTask);
                }
                if (!tasksInError.add(errorTask)) {
                    log.warn("Notified more than once of errors task {}", errorTask);
                }
            }
            if (!canRunTask()) {
                // Some task notified errors and the workflow is suspended as no task can continue anymore
                finishedCondition.signalAll();
                listeners.forEach(this::notifyErrorToListener);
            }
        } finally {
            lock.unlock();
        }
    }

    private boolean canRunTask() {
        // Check to see if the deployment can continue
        boolean canRunTask = false;
        for (AbstractTask task : tasksLeft) {
            if (task.canRun()) {
                canRunTask = true;
                break;
            }
        }
        return canRunTask;
    }

    private boolean checkCyclicDependencies() {
        // Check to see if the deployment can continue
        boolean canRunTask = canRunTask();
        if (!canRunTask) {
            log.error("Cyclic dependencies detected: \n {}", toString());
            onTaskFailure(null, new InvalidWorkflowException("Cyclic dependencies detected, cannot process workflow execution anymore"));
        }
        return canRunTask;
    }

    public void onTaskCompletion(AbstractTask completedTask) {
        try {
            lock.lock();
            if (!tasksLeft.remove(completedTask)) {
                log.warn("Notified of completion of unknown task {}", completedTask);
            } else if (!errors.isEmpty() && !canRunTask()) {
                // Some task notified errors and the workflow is suspended as no task can continue anymore
                finishedCondition.signalAll();
                listeners.forEach(this::notifyErrorToListener);
            } else if (isFinished()) {
                // No task left
                finishedCondition.signalAll();
                listeners.forEach(this::notifyCompletionToListener);
                // Gracefully shut down the execution
                shutdown(true);
            } else {
                checkCyclicDependencies();
            }
        } finally {
            lock.unlock();
        }
    }

    private void notifyCompletionToListener(WorkflowExecutionListener listener) {
        try {
            listener.onFinish();
        } catch (Throwable e) {
            log.error("Listener throw unexpected errors", e);
            errors.add(e);
            notifyErrorToListener(listener);
        }
    }

    private void notifyErrorToListener(WorkflowExecutionListener listener) {
        try {
            listener.onFailure(errors);
        } catch (Throwable fe) {
            log.error("Listener throw unexpected errors", fe);
        }
    }

    public void addListener(WorkflowExecutionListener listener) {
        try {
            lock.lock();
            if (!errors.isEmpty()) {
                notifyErrorToListener(listener);
            } else if (isFinished()) {
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
            if (isFinished()) {
                return true;
            } else if (!errors.isEmpty()) {
                throw errors.iterator().next();
            } else {
                boolean signaled = finishedCondition.await(timeout, unit);
                if (!signaled) {
                    return false;
                } else if (!errors.isEmpty()) {
                    throw errors.iterator().next();
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

    public void launch() {
        try {
            lock.lock();
            if (checkCyclicDependencies()) {
                tasksLeft.stream().filter(AbstractTask::canRun).forEach(task -> executorService.submit(task));
            }
        } finally {
            lock.unlock();
        }
    }

    public void resume() {
        try {
            lock.lock();
            errors.clear();
            tasksLeft.addAll(tasksInError);
            tasksInError.clear();
            launch();
        } finally {
            lock.unlock();
        }
    }

    public void shutdown(boolean gracefully) {
        try {
            lock.lock();
            if (gracefully) {
                executorService.shutdown();
            } else {
                executorService.shutdownNow();
            }
        } finally {
            lock.unlock();
        }
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
