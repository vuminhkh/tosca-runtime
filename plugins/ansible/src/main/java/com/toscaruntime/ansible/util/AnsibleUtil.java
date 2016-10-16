package com.toscaruntime.ansible.util;

import com.toscaruntime.ansible.connection.AnsibleDockerConnection;
import com.toscaruntime.ansible.connection.AnsibleSSHConnection;
import com.toscaruntime.artifact.Connection;
import com.toscaruntime.common.nodes.DockerContainer;
import com.toscaruntime.common.nodes.LinuxCompute;
import com.toscaruntime.exception.deployment.plugins.PluginConfigurationException;
import tosca.nodes.Root;

public class AnsibleUtil {

    private static Class<? extends Connection> getConnectionType(String type, Class<? extends Connection> defaultType) {
        if (type == null) {
            return defaultType;
        }
        switch (type) {
            case "ssh":
                return AnsibleSSHConnection.class;
            case "docker":
                return AnsibleDockerConnection.class;
            default:
                throw new PluginConfigurationException("Connection type [" + type + "] is not supported");
        }
    }

    public static Class<? extends Connection> getConnectionType(Root node, String connectionType) {
        if (node instanceof LinuxCompute) {
            // A Linux VM needs SSH connection by default to execute bash script
            return getConnectionType(connectionType, AnsibleSSHConnection.class);
        } else if (node instanceof DockerContainer) {
            // A docker container needs docker exec connection by default to execute bash script
            return getConnectionType(connectionType, AnsibleDockerConnection.class);
        } else {
            // Do nothing if cannot manage the type of compute
            return null;
        }
    }
}
