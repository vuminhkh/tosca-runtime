package com.toscaruntime.ansible.connection;

import com.toscaruntime.exception.deployment.artifact.BadExecutorConfigurationException;
import org.apache.commons.lang.StringUtils;

import java.util.Map;

public class AnsibleSSHConnection extends AnsibleConnection {

    @Override
    public void initialize(Map<String, Object> properties) {
        super.initialize(properties);
        if (StringUtils.isBlank(this.getUser())) {
            throw new BadExecutorConfigurationException("User must be set for SSH connection");
        }
        if (StringUtils.isBlank(this.getRemoteKeyFile())) {
            throw new BadExecutorConfigurationException("Key must be set for SSH connection");
        }
    }
}
