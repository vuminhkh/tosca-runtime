package com.toscaruntime.sdk.workflow;

import java.util.Collection;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.toscaruntime.deployment.DeploymentPersister;

/**
 * Default listener for workflow execution, which do nothing than persist execution's state
 *
 * @author Minh Khang VU
 */
public class DefaultListener implements Listener {

    private static final Logger log = LoggerFactory.getLogger(DefaultListener.class);

    private DeploymentPersister deploymentPersister;

    private String workflowId;

    public DefaultListener(DeploymentPersister deploymentPersister, String workflowId) {
        this.deploymentPersister = deploymentPersister;
        this.workflowId = workflowId;
    }

    @Override
    public void onStop() {
        deploymentPersister.syncStopRunningExecution();
        log.info("Execution for {} workflow has been stopped", workflowId);
    }

    @Override
    public void onCancel() {
        deploymentPersister.syncCancelRunningExecution();
        log.info("Execution for {} workflow has been cancelled", workflowId);
    }

    @Override
    public void onFinish() {
        deploymentPersister.syncFinishRunningExecution();
        log.info("Execution for {} workflow has finished successfully", workflowId);
    }

    @Override
    public void onFailure(Collection<Throwable> errors) {
        errors.forEach(error -> log.error("Execution for workflow " + workflowId + " encountered error", error));
        deploymentPersister.syncStopRunningExecution(errors.stream().map(Throwable::getMessage).collect(Collectors.joining(", ")));
    }
}
