package com.toscaruntime.sdk.workflow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            tasksLeft.remove(completedTask);
            if (tasksLeft.isEmpty()) {
                try {
                    listeners.forEach(WorkflowExecutionListener::onFinish);
                } catch (Throwable e) {
                    log.error("Workflow execution encountered unexpected error in listener", e);
                } finally {
                    finishedCondition.signalAll();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void addListener(WorkflowExecutionListener listener) {
        try {
            lock.lock();
            if (error != null) {
                listener.onFailure(error);
            } else if (tasksLeft.isEmpty()) {
                listener.onFinish();
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

}
