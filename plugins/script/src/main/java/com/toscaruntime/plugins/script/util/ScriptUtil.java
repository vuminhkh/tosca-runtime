package com.toscaruntime.plugins.script.util;

import com.toscaruntime.artifact.Connection;
import com.toscaruntime.common.nodes.DockerContainer;
import com.toscaruntime.common.nodes.LinuxCompute;
import com.toscaruntime.exception.deployment.plugins.PluginConfigurationException;
import com.toscaruntime.util.DockerConnection;
import com.toscaruntime.util.SSHConnection;
import tosca.nodes.Root;

public class ScriptUtil {

    private static Class<? extends Connection> getConnectionType(String type, Class<? extends Connection> defaultType) {
        if (type == null) {
            return defaultType;
        }
        switch (type) {
            case "ssh":
                return SSHConnection.class;
            case "docker":
                return DockerConnection.class;
            default:
                throw new PluginConfigurationException("Connection type [" + type + "] is not supported");
        }
    }

    public static Class<? extends Connection> getConnectionType(Root node, String connectionType) {
        if (node instanceof LinuxCompute) {
            // A Linux VM needs SSH connection by default to execute bash script
            return getConnectionType(connectionType, SSHConnection.class);
        } else if (node instanceof DockerContainer) {
            // A docker container needs docker exec connection by default to execute bash script
            return getConnectionType(connectionType, DockerConnection.class);
        } else {
            // Do nothing if cannot manage the type of compute
            return null;
        }
    }
}
