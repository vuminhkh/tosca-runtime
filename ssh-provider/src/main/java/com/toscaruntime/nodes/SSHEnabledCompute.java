package com.toscaruntime.nodes;

import com.toscaruntime.exception.deployment.artifact.ArtifactAuthenticationFailureException;
import com.toscaruntime.exception.deployment.artifact.ArtifactConnectException;
import com.toscaruntime.exception.deployment.artifact.ArtifactExecutionException;
import com.toscaruntime.exception.deployment.artifact.ArtifactInterruptedException;
import com.toscaruntime.exception.deployment.configuration.PropertyRequiredException;
import com.toscaruntime.exception.deployment.execution.InvalidOperationExecutionException;
import com.toscaruntime.util.ArtifactExecutionUtil;
import com.toscaruntime.util.FailSafeUtil;
import com.toscaruntime.util.SSHJExecutor;
import com.toscaruntime.util.ToscaUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tosca.nodes.Compute;

import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Compute which is accessible by SSH
 */
public abstract class SSHEnabledCompute extends Compute {

    private static final Logger log = LoggerFactory.getLogger(SSHEnabledCompute.class);

    protected SSHJExecutor artifactExecutor;

    protected String ipAddress;

    private SSHJExecutor createExecutor(String ipForSSSHSession) {
        // TODO code duplication need refactoring artifact executor
        String user = getMandatoryPropertyAsString("login");
        String keyPath = getPropertyAsString("key_path");
        String keyContent = getPropertyAsString("key_content");
        Integer port = Integer.parseInt(getPropertyAsString("ssh_port", "22"));
        boolean elevatePrivilege = Boolean.parseBoolean(getPropertyAsString("elevate_privilege"));
        if (StringUtils.isNotBlank(keyPath)) {
            String absoluteKeyPath;
            if (Paths.get(keyPath).isAbsolute()) {
                absoluteKeyPath = keyPath;
            } else {
                absoluteKeyPath = this.config.getTopologyResourcePath().resolve(keyPath).toString();
            }
            return new SSHJExecutor(user, ipForSSSHSession, port, Paths.get(absoluteKeyPath), elevatePrivilege);
        } else {
            if (StringUtils.isBlank(keyContent)) {
                throw new PropertyRequiredException("One of key_path or key_content is required to connect to the created VM");
            }
            return new SSHJExecutor(user, ipForSSSHSession, port, keyContent, elevatePrivilege);
        }
    }

    protected String getIpAddressForSSHSession() {
        // TODO code duplication need refactoring artifact executor
        if (!this.config.isBootstrap()) {
            return ipAddress;
        } else {
            String attachedFloatingIP = getAttributeAsString("public_ip_address");
            if (StringUtils.isBlank(attachedFloatingIP)) {
                log.warn("Compute [{}] : Bootstrap mode is enabled but no public_ip_address can be found on the compute, will use private ip [{}]", getId(), ipAddress);
                return ipAddress;
            } else {
                log.info("Compute [{}] : Bootstrap mode is enabled, use public ip [{}] to initialize SSH session", getId(), attachedFloatingIP);
                return attachedFloatingIP;
            }
        }
    }

    protected void destroySshExecutor() {
        if (artifactExecutor != null) {
            artifactExecutor.close();
        }
    }

    protected void initSshExecutor(String ipForSSSHSession) {
        if (StringUtils.isBlank(ipForSSSHSession)) {
            throw new InvalidOperationExecutionException("Compute [" + getId() + "] : IP of the server " + getId() + "is null, maybe it was not initialized properly or has been deleted");
        }
        String operationName = "Create ssh session for " + getId();
        int connectRetry = getConnectRetry();
        long waitBetweenConnectRetry = getWaitBetweenConnectRetry();
        try {
            // Create the executor
            this.artifactExecutor = createExecutor(ipForSSSHSession);
            // Wait before initializing the connection
            Thread.sleep(TimeUnit.SECONDS.toMillis(getWaitBeforeConnection()));
            // Initialize the connection
            FailSafeUtil.doActionWithRetry(() -> artifactExecutor.initialize(), operationName, connectRetry, waitBetweenConnectRetry, TimeUnit.SECONDS, ArtifactConnectException.class, ArtifactAuthenticationFailureException.class);
            // Sometimes the created VM needs sometimes to initialize and to resolve DNS
            Thread.sleep(TimeUnit.SECONDS.toMillis(getWaitBeforeArtifactExecution()));
            // Upload the recipe to the remote host
            FailSafeUtil.doActionWithRetry(this::uploadRecipe, "Upload recipe", connectRetry, waitBetweenConnectRetry, TimeUnit.SECONDS, ArtifactConnectException.class);
        } catch (ArtifactInterruptedException e) {
            throw e;
        } catch (Throwable e) {
            log.error("Compute [" + getId() + "] : Unable to create ssh session", e);
            throw new InvalidOperationExecutionException("Compute [" + getId() + "] : Unable to create ssh session", e);
        }
    }

    @Override
    public void initialLoad() {
        super.initialLoad();
        this.ipAddress = getAttributeAsString("ip_address");
        this.artifactExecutor = createExecutor(getIpAddressForSSHSession());
        this.artifactExecutor.initialize();
    }

    @Override
    public void uploadRecipe() {
        if (artifactExecutor == null) {
            log.warn("Compute is not fully initialized, ignoring recipe update request");
            return;
        }
        String recipeLocation = getMandatoryPropertyAsString("recipe_location");
        artifactExecutor.upload(this.config.getArtifactsPath().toString(), recipeLocation);
    }

    protected Map<String, String> executeBySSH(String nodeId, String operationArtifactPath, Map<String, Object> inputs, Map<String, String> deploymentArtifacts) {
        String recipeLocation = getMandatoryPropertyAsString("recipe_location");
        try {
            return FailSafeUtil.doActionWithRetry(
                    () -> artifactExecutor.executeArtifact(
                            nodeId,
                            config.getArtifactsPath().resolve(operationArtifactPath),
                            recipeLocation + "/" + operationArtifactPath,
                            ArtifactExecutionUtil.processInputs(inputs, deploymentArtifacts, recipeLocation, "/")
                    ),
                    operationArtifactPath,
                    getArtifactExecutionRetry(),
                    getWaitBetweenArtifactExecutionRetry(),
                    TimeUnit.SECONDS,
                    ArtifactConnectException.class,
                    ArtifactExecutionException.class);
        } catch (ArtifactInterruptedException e) {
            log.info("Compute [{}][{}] has been interrupted", getId(), operationArtifactPath);
            throw e;
        } catch (Throwable e) {
            throw new InvalidOperationExecutionException("Compute [" + getId() + "] : Unable to execute operation " + operationArtifactPath, e);
        }
    }

    private long getWaitBeforeConnection() {
        String waitBeforeConnection = getMandatoryPropertyAsString("compute_fail_safe.wait_before_connection");
        return ToscaUtil.convertToSeconds(waitBeforeConnection);
    }

    private int getConnectRetry() {
        return Integer.parseInt(getMandatoryPropertyAsString("compute_fail_safe.connect_retry"));
    }

    private long getWaitBetweenConnectRetry() {
        String waitBetweenConnectRetry = getMandatoryPropertyAsString("compute_fail_safe.wait_between_connect_retry");
        return ToscaUtil.convertToSeconds(waitBetweenConnectRetry);
    }

    protected int getArtifactExecutionRetry() {
        return Integer.parseInt(getMandatoryPropertyAsString("compute_fail_safe.artifact_execution_retry"));
    }

    protected long getWaitBetweenArtifactExecutionRetry() {
        String waitBetweenArtifactExecutionRetry = getMandatoryPropertyAsString("compute_fail_safe.wait_between_artifact_execution_retry");
        return ToscaUtil.convertToSeconds(waitBetweenArtifactExecutionRetry);
    }

    private long getWaitBeforeArtifactExecution() {
        String waitBeforeArtifactExecution = getMandatoryPropertyAsString("compute_fail_safe.wait_before_artifact_execution");
        return ToscaUtil.convertToSeconds(waitBeforeArtifactExecution);
    }
}
