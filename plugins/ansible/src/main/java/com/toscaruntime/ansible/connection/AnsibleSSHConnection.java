package com.toscaruntime.ansible.connection;

import com.toscaruntime.exception.deployment.artifact.ArtifactConnectException;
import com.toscaruntime.exception.deployment.artifact.BadExecutorConfigurationException;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class AnsibleSSHConnection extends AnsibleConnection {

    private static final Logger log = LoggerFactory.getLogger(AnsibleConnection.class);

    @Override
    public void initialize(Map<String, Object> properties) {
        super.initialize(properties);
        if (StringUtils.isBlank(this.getUser())) {
            throw new BadExecutorConfigurationException("User must be set for SSH connection");
        }
        if (StringUtils.isBlank(this.getRemoteKeyFile())) {
            throw new BadExecutorConfigurationException("Key must be set for SSH connection");
        }
        String waitForSSHCommand = getAnsibleBinPath() + " all -i localhost, --connection=local --module-name=wait_for --args='host=" + getTarget() + " port=" + getPort() + " connect_timeout=" + getConnectTimeout() + " delay=" + getDelay() + "'";
        log.info("Wait for SSH on the target with command [" + waitForSSHCommand + "]");
        Integer waitForSSH = executeCommandOnControlMachine(waitForSSHCommand);
        if (waitForSSH == null || waitForSSH != 0) {
            throw new ArtifactConnectException("Could not wait for SSH connection to be available on the target [" + getTarget() + ":" + getPort() + "]");
        }
    }
}
