package com.toscaruntime.sdk.workflow;

import com.toscaruntime.constant.ExecutionConstant;
import com.toscaruntime.deployment.DeploymentPersister;
import com.toscaruntime.deployment.NodeTaskDTO;
import com.toscaruntime.deployment.RelationshipTaskDTO;
import com.toscaruntime.deployment.TaskDTO;
import com.toscaruntime.exception.deployment.workflow.InvalidWorkflowCommandException;
import com.toscaruntime.exception.deployment.workflow.InvalidWorkflowException;
import com.toscaruntime.sdk.workflow.tasks.AbstractGenericTask;
import com.toscaruntime.sdk.workflow.tasks.AbstractTask;
import com.toscaruntime.sdk.workflow.tasks.nodes.AbstractNodeTask;
import com.toscaruntime.sdk.workflow.tasks.relationships.AbstractRelationshipTask;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tosca.relationships.Root;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class WorkflowExecution {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecution.class);

    private String workflowId;

    private Set<AbstractTask> totalTasks = new HashSet<>();

    private Set<AbstractTask> tasksLeft = new HashSet<>();

    private Map<AbstractTask, Throwable> tasksInError = new HashMap<>();

    private Map<AbstractTask, Future> tasksRunning = new HashMap<>();

    private ReentrantLock lock = new ReentrantLock();

    private Condition finishedCondition = lock.newCondition();

    private boolean isCancelled = false;

    private boolean isInterrupted = false;

    private boolean isStoppedByError = false;

    private List<Listener> listeners = new ArrayList<>();

    private ExecutorService executorService;

    private DeploymentPersister deploymentPersister;

    public WorkflowExecution(String workflowId, ExecutorService executorService) {
        this.workflowId = workflowId;
        this.executorService = executorService;
    }

    public WorkflowExecution(String workflowId, ExecutorService executorService, DeploymentPersister deploymentPersister) {
        this(workflowId, executorService);
        this.deploymentPersister = deploymentPersister;
    }

    public boolean isTransient() {
        return this.deploymentPersister == null;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public ReentrantLock getLock() {
        return lock;
    }

    /**
     * Perform initial loads from persistence to take into account only non executed tasks
     *
     * @param nodeTaskDTOs         map of node task to task status
     * @param relationshipTaskDTOs map of relationship task to task status
     * @param taskDTOs             map of generic task to task status
     */
    public void initialLoad(Map<NodeTaskDTO, String> nodeTaskDTOs, Map<RelationshipTaskDTO, String> relationshipTaskDTOs, Map<TaskDTO, String> taskDTOs) {
        // Filter already finished tasks
        Set<AbstractTask> allToBeRun = tasksLeft.stream().filter(task -> {
            if (task instanceof AbstractNodeTask) {
                AbstractNodeTask nodeTask = (AbstractNodeTask) task;
                return !nodeTaskDTOs.get(new NodeTaskDTO(nodeTask.getNodeInstance().getId(), nodeTask.getInterfaceName(), nodeTask.getOperationName())).equals(ExecutionConstant.SUCCESS);
            } else if (task instanceof AbstractRelationshipTask) {
                AbstractRelationshipTask relationshipTask = (AbstractRelationshipTask) task;
                Root relationship = relationshipTask.getRelationshipInstance();
                return !relationshipTaskDTOs.get(new RelationshipTaskDTO(relationship.getSource().getId(), relationship.getTarget().getId(), relationship.getNode().getRelationshipName(), relationshipTask.getInterfaceName(), relationshipTask.getOperationName())).equals(ExecutionConstant.SUCCESS);
            } else {
                AbstractGenericTask genericTask = (AbstractGenericTask) task;
                return !taskDTOs.get(new TaskDTO(genericTask.getTaskId())).equals(ExecutionConstant.SUCCESS);
            }
        }).collect(Collectors.toSet());
        allToBeRun.stream().forEach(toBeRun -> {
            // Filter out tasks that has already finished from the dependencies
            Set<AbstractTask> toBeRunDependencies = toBeRun.getDependsOnTasks().stream().filter(allToBeRun::contains).collect(Collectors.toSet());
            toBeRun.setDependsOnTasks(toBeRunDependencies);
        });
        tasksLeft = allToBeRun;
    }

    public void onTaskFailure(AbstractTask errorTask, Throwable t) {
        try {
            lock.lock();
            if (tasksRunning.remove(errorTask) == null) {
                log.warn("Notified of errors of unknown task {}", errorTask);
            } else {
                if (!isTransient()) {
                    if (errorTask instanceof AbstractNodeTask) {
                        AbstractNodeTask nodeTask = (AbstractNodeTask) errorTask;
                        deploymentPersister.syncStopNodeTask(nodeTask.getNodeInstance().getId(), nodeTask.getInterfaceName(), nodeTask.getOperationName(), t.getMessage());
                    } else if (errorTask instanceof AbstractRelationshipTask) {
                        AbstractRelationshipTask relationshipTask = (AbstractRelationshipTask) errorTask;
                        Root relationship = relationshipTask.getRelationshipInstance();
                        deploymentPersister.syncStopRelationshipTask(relationship.getSource().getId(), relationship.getTarget().getId(), relationship.getNode().getRelationshipName(), relationshipTask.getInterfaceName(), relationshipTask.getOperationName(), t.getMessage());
                    } else {
                        deploymentPersister.syncStopTask(((AbstractGenericTask) errorTask).getTaskId(), t.getMessage());
                    }
                }
            }
            if (tasksInError.put(errorTask, t) != null) {
                log.warn("Notified more than once of errors task {}", errorTask);
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
            } else {
                if (!isTransient()) {
                    if (completedTask instanceof AbstractNodeTask) {
                        AbstractNodeTask nodeTask = (AbstractNodeTask) completedTask;
                        deploymentPersister.syncFinishNodeTask(nodeTask.getNodeInstance().getId(), nodeTask.getInterfaceName(), nodeTask.getOperationName());
                    } else if (completedTask instanceof AbstractRelationshipTask) {
                        AbstractRelationshipTask relationshipTask = (AbstractRelationshipTask) completedTask;
                        Root relationship = relationshipTask.getRelationshipInstance();
                        deploymentPersister.syncFinishRelationshipTask(relationship.getSource().getId(), relationship.getTarget().getId(), relationship.getNode().getRelationshipName(), relationshipTask.getInterfaceName(), relationshipTask.getOperationName());
                    } else {
                        deploymentPersister.syncFinishTask(((AbstractGenericTask) completedTask).getTaskId());
                    }
                }
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
            log.info("Execution of workflow {} has been stopped, number of tasks not executed {}, number of tasks interrupted {}, number of tasks in error {}, total number of tasks {}", workflowId, tasksLeft.size(), numberOfTasksInterrupted, tasksInError.values().size() - numberOfTasksInterrupted, totalTasks.size());
            listeners.forEach(this::notifyInterruptedToListener);
            tasksLeft.addAll(tasksInError.keySet());
            tasksInError.clear();
            return true;
        } else if (!tasksInError.isEmpty()) {
            log.info("Execution of workflow {} has been stopped by error, number of tasks not executed {}, number of tasks in error {}, total number of tasks {}", workflowId, tasksLeft.size(), tasksInError.values().size(), totalTasks.size());
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

    private void notifyCompletionToListener(Listener listener) {
        try {
            listener.onFinish();
        } catch (Throwable e) {
            log.error("Listener onFinish throw unexpected errors", e);
        }
    }

    private void notifyErrorToListener(Listener listener, Collection<Throwable> errors) {
        try {
            listener.onFailure(errors);
        } catch (Throwable fe) {
            log.error("Listener onFailure throw unexpected errors", fe);
        }
    }

    private void notifyInterruptedToListener(Listener listener) {
        try {
            listener.onStop();
        } catch (Throwable fe) {
            log.error("Listener onStop throw unexpected errors", fe);
        }
    }

    private void notifyCancelledToListener(Listener listener) {
        try {
            listener.onCancel();
        } catch (Throwable fe) {
            log.error("Listener onCancel throw unexpected errors", fe);
        }
    }

    public void addListener(Listener listener) {
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
        totalTasks.addAll(tasks);
        tasks.stream().forEach(task -> task.setWorkflowExecution(this));
    }

    public Set<AbstractTask> getTasksLeft() {
        return tasksLeft;
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
                    if (!isTransient()) {
                        if (task instanceof AbstractNodeTask) {
                            AbstractNodeTask nodeTask = (AbstractNodeTask) task;
                            deploymentPersister.syncStartNodeTask(nodeTask.getNodeInstance().getId(), nodeTask.getInterfaceName(), nodeTask.getOperationName());
                        } else if (task instanceof AbstractRelationshipTask) {
                            AbstractRelationshipTask relationshipTask = (AbstractRelationshipTask) task;
                            Root relationship = relationshipTask.getRelationshipInstance();
                            deploymentPersister.syncStartRelationshipTask(relationship.getSource().getId(), relationship.getTarget().getId(), relationship.getNode().getRelationshipName(), relationshipTask.getInterfaceName(), relationshipTask.getOperationName());
                        } else {
                            deploymentPersister.syncStartTask(((AbstractGenericTask) task).getTaskId());
                        }
                    }
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

    public void persist() {
        // Persist tasks when they are added to the execution
        if (!isTransient()) {
            for (AbstractTask task : totalTasks) {
                if (task instanceof AbstractNodeTask) {
                    AbstractNodeTask nodeTask = (AbstractNodeTask) task;
                    deploymentPersister.syncInsertNewNodeTask(nodeTask.getNodeInstance().getId(), nodeTask.getInterfaceName(), nodeTask.getOperationName());
                } else if (task instanceof AbstractRelationshipTask) {
                    AbstractRelationshipTask relationshipTask = (AbstractRelationshipTask) task;
                    Root relationship = relationshipTask.getRelationshipInstance();
                    deploymentPersister.syncInsertNewRelationshipTask(relationship.getSource().getId(), relationship.getTarget().getId(), relationship.getNode().getRelationshipName(), relationshipTask.getInterfaceName(), relationshipTask.getOperationName());
                } else {
                    deploymentPersister.syncInsertNewTask(((AbstractGenericTask) task).getTaskId());
                }
            }
        }
    }

    public void launch() {
        doLaunch(tasksLeft);
    }

    public void resume() {
        try {
            lock.lock();
            log.info("Trying to resume execution of workflow {}", workflowId);
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
                log.info("Trying to stop execution, any running tasks will be interrupted");
            } else {
                log.info("Trying to stop execution gracefully, all running tasks will continue to run, but no new tasks will be submitted");
            }
            if (!tasksRunning.isEmpty()) {
                tasksRunning.values().forEach(task -> task.cancel(force));
            } else {
                listeners.forEach(this::notifyInterruptedToListener);
                listeners.clear();
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
                listeners.clear();
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
