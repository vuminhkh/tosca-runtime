package com.toscaruntime.sdk.workflow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.toscaruntime.exception.deployment.workflow.InvalidWorkflowCommandException;
import com.toscaruntime.exception.deployment.workflow.InvalidWorkflowException;
import com.toscaruntime.sdk.workflow.tasks.AbstractTask;

public class WorkflowExecution {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecution.class);

    private Set<AbstractTask> tasksLeft = new HashSet<>();

    private Map<AbstractTask, Throwable> tasksInError = new HashMap<>();

    private Map<AbstractTask, Future> tasksRunning = new HashMap<>();

    private ReentrantLock lock = new ReentrantLock();

    private Condition finishedCondition = lock.newCondition();

    private boolean isCancelled = false;

    private boolean isInterrupted = false;

    private boolean isStoppedByError = false;

    private List<WorkflowExecutionListener> listeners = new ArrayList<>();

    private ExecutorService executorService;

    public WorkflowExecution(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public ReentrantLock getLock() {
        return lock;
    }

    public void onTaskFailure(AbstractTask errorTask, Throwable t) {
        try {
            lock.lock();
            if (errorTask != null) {
                if (tasksRunning.remove(errorTask) == null) {
                    log.warn("Notified of errors of unknown task {}", errorTask);
                }
                if (tasksInError.put(errorTask, t) != null) {
                    log.warn("Notified more than once of errors task {}", errorTask);
                }
            }
            if (tryFinishingExecution()) {
                finishedCondition.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    public void onTaskCompletion(AbstractTask completedTask) {
        try {
            lock.lock();
            if (tasksRunning.remove(completedTask) == null) {
                log.warn("Notified of completion of unknown task {}", completedTask);
            }
            if (tryFinishingExecution()) {
                finishedCondition.signalAll();
            } else {
                // Check if tasks can be run, if yes run it
                doLaunch(completedTask.getDependedByTasks());
            }
        } finally {
            lock.unlock();
        }
    }

    private boolean tryFinishingExecution() {
        if (!tasksRunning.isEmpty()) {
            // Has running tasks then so cannot finish the execution
            return false;
        }
        if (isCancelled) {
            listeners.forEach(this::notifyCancelledToListener);
            listeners.clear();
            return true;
        } else if (isInterrupted) {
            long numberOfTasksInterrupted = tasksInError.values().stream().filter(error -> ExceptionUtils.indexOfType(error, InterruptedException.class) > -1).count();
            log.info("Execution has been stopped, number of tasks not executed {}, number of tasks interrupted {}, number of tasks in error {}", tasksLeft.size(), numberOfTasksInterrupted, tasksInError.values().size() - numberOfTasksInterrupted);
            listeners.forEach(this::notifyInterruptedToListener);
            tasksLeft.addAll(tasksInError.keySet());
            tasksInError.clear();
            return true;
        } else if (!tasksInError.isEmpty()) {
            log.info("Execution has been stopped by error, number of tasks not executed {}, number of tasks in error {}", tasksLeft.size(), tasksInError.values().size());
            listeners.forEach(listener -> notifyErrorToListener(listener, tasksInError.values()));
            isStoppedByError = true;
            tasksLeft.addAll(tasksInError.keySet());
            tasksInError.clear();
            return true;
        } else if (tasksLeft.isEmpty()) {
            listeners.forEach(this::notifyCompletionToListener);
            executorService.shutdown();
            return true;
        } else {
            return false;
        }
    }

    private void notifyCompletionToListener(WorkflowExecutionListener listener) {
        try {
            listener.onFinish();
        } catch (Throwable e) {
            log.error("Listener onFinish throw unexpected errors", e);
        }
    }

    private void notifyErrorToListener(WorkflowExecutionListener listener, Collection<Throwable> errors) {
        try {
            listener.onFailure(errors);
        } catch (Throwable fe) {
            log.error("Listener onFailure throw unexpected errors", fe);
        }
    }

    private void notifyInterruptedToListener(WorkflowExecutionListener listener) {
        try {
            listener.onStop();
        } catch (Throwable fe) {
            log.error("Listener onStop throw unexpected errors", fe);
        }
    }

    private void notifyCancelledToListener(WorkflowExecutionListener listener) {
        try {
            listener.onCancel();
        } catch (Throwable fe) {
            log.error("Listener onCancel throw unexpected errors", fe);
        }
    }

    public void addListener(WorkflowExecutionListener listener) {
        try {
            lock.lock();
            listeners.add(listener);
        } finally {
            lock.unlock();
        }
    }

    private boolean checkWorkflowExecutionFinish() throws Throwable {
        if ((tasksLeft.isEmpty() && tasksRunning.isEmpty() && tasksInError.isEmpty())) {
            return true;
        } else if (isCancelled) {
            throw new InterruptedException("Workflow execution has been cancelled");
        } else if (isInterrupted) {
            throw new InterruptedException("Workflow execution has been interrupted");
        } else if (!tasksInError.isEmpty()) {
            throw tasksInError.values().iterator().next();
        } else {
            return false;
        }
    }


    public boolean waitForCompletion(long timeout, TimeUnit unit) throws Throwable {
        try {
            lock.lock();
            // It must finish immediately or else waiting for the execution to finish must succeed, and when awaken, then the recheck the execution state
            return checkWorkflowExecutionFinish() || (finishedCondition.await(timeout, unit) && checkWorkflowExecutionFinish());
        } finally {
            lock.unlock();
        }
    }

    public void addTasks(List<AbstractTask> tasks) {
        tasksLeft.addAll(tasks);
    }

    private void doLaunch(Set<AbstractTask> tasksToRun) {
        try {
            lock.lock();
            Set<AbstractTask> tasksCanBeRun = tasksToRun.stream().filter(task -> task.canRun() && !tasksRunning.containsKey(task)).collect(Collectors.toSet());
            if (tasksCanBeRun.isEmpty()) {
                if (tasksRunning.isEmpty()) {
                    // Check to see if the deployment can continue
                    boolean canRunTask = false;
                    for (AbstractTask task : tasksLeft) {
                        if (task.canRun()) {
                            canRunTask = true;
                            break;
                        }
                    }
                    if (!canRunTask) {
                        log.error("Cyclic dependencies detected: \n {}", toString());
                        finishedCondition.signalAll();
                        listeners.forEach(listener -> notifyErrorToListener(listener, Collections.singleton(new InvalidWorkflowException("Cyclic dependency detected in the workflow"))));
                    }
                }
            } else {
                tasksCanBeRun.stream().forEach(task -> {
                    if (!tasksLeft.remove(task)) {
                        log.error("Running unknown task {}", task);
                    }
                    if (tasksRunning.put(task, executorService.submit(task)) != null) {
                        log.error("Running {} more than once", task);
                    }
                });
            }
        } finally {
            lock.unlock();
        }
    }

    public void launch() {
        doLaunch(tasksLeft);
    }

    public void resume() {
        try {
            lock.lock();
            log.info("Trying to resume execution of workflow");
            isInterrupted = false;
            isStoppedByError = false;
            launch();
        } finally {
            lock.unlock();
        }
    }

    public void stop(boolean force) {
        try {
            lock.lock();
            if (isInterrupted) {
                throw new InvalidWorkflowCommandException("Cannot stop workflow execution as it has already been stopped");
            }
            if (isStoppedByError) {
                throw new InvalidWorkflowCommandException("Cannot stop workflow execution as it has already been stopped by error");
            }
            isInterrupted = true;
            if (force) {
                log.info("Trying to stop execution");
            } else {
                log.info("Trying to stop execution gracefully");
            }
            if (!tasksRunning.isEmpty()) {
                tasksRunning.values().forEach(task -> task.cancel(force));
            } else {
                listeners.forEach(this::notifyInterruptedToListener);
            }
        } finally {
            lock.unlock();
        }
    }

    public void cancel(boolean force) {
        try {
            lock.lock();
            isCancelled = true;
            executorService.shutdownNow();
            if (force || tasksRunning.isEmpty()) {
                // If not forcing the cancel then the listeners will be notified only when every tasks have successfully been cancelled
                listeners.forEach(this::notifyCancelledToListener);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String toString() {
        try {
            lock.lock();
            StringBuilder buffer = new StringBuilder("Workflow execution has ").append(tasksLeft.size()).append(" tasks left:\n");
            for (AbstractTask task : tasksLeft) {
                buffer.append("\t- ").append(task).append(" depends on [").append(task.getDependsOnTasks()).append("]\n");
            }
            return buffer.toString();
        } finally {
            lock.unlock();
        }
    }
}
