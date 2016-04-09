package com.toscaruntime.sdk.workflow;

import com.toscaruntime.deployment.DeploymentPersister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Default listener for workflow execution, which do nothing than persist execution's state
 *
 * @author Minh Khang VU
 */
public class DefaultListener implements Listener {

    private static final Logger log = LoggerFactory.getLogger(DefaultListener.class);

    private DeploymentPersister deploymentPersister;

    private WorkflowExecution workflowExecution;

    public DefaultListener(DeploymentPersister deploymentPersister, WorkflowExecution workflowExecution) {
        this.deploymentPersister = deploymentPersister;
        this.workflowExecution = workflowExecution;
    }

    @Override
    public void onStop() {
        if (!this.workflowExecution.isTransient()) {
            deploymentPersister.syncStopRunningExecution();
        }
        log.info("Execution for {} workflow has been stopped", this.workflowExecution.getWorkflowId());
    }

    @Override
    public void onCancel() {
        if (!this.workflowExecution.isTransient()) {
            deploymentPersister.syncCancelRunningExecution();
        }
        log.info("Execution for {} workflow has been cancelled", this.workflowExecution.getWorkflowId());
    }

    @Override
    public void onFinish() {
        if (!this.workflowExecution.isTransient()) {
            deploymentPersister.syncFinishRunningExecution();
        }
        log.info("Execution for {} workflow has finished successfully", this.workflowExecution.getWorkflowId());
    }

    @Override
    public void onFailure(Collection<Throwable> errors) {
        errors.forEach(error -> log.error("Execution for workflow " + this.workflowExecution.getWorkflowId() + " encountered error", error));
        if (!this.workflowExecution.isTransient()) {
            deploymentPersister.syncStopRunningExecution(errors.stream().map(Throwable::getMessage).collect(Collectors.joining(", ")));
        }
    }
}
