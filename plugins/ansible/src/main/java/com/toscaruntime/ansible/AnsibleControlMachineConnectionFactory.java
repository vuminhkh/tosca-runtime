package com.toscaruntime.ansible;

import com.toscaruntime.artifact.Connection;
import com.toscaruntime.configuration.ConnectionFactory;
import com.toscaruntime.exception.deployment.plugins.PluginConfigurationException;
import com.toscaruntime.util.DockerConnection;
import com.toscaruntime.util.PropertyUtil;
import com.toscaruntime.util.SSHConnection;

import java.util.Map;

public class AnsibleControlMachineConnectionFactory implements ConnectionFactory<Connection> {

    @Override
    public Connection newConnection(Map<String, Object> properties, Map<String, Object> bootstrapContext, boolean multipleTargets) {
        String connectionType = PropertyUtil.getMandatoryPropertyAsString(properties, "connection_type");
        switch (connectionType) {
            case "ssh":
                SSHConnection sshConnection = new SSHConnection();
                sshConnection.initialize(properties);
                return sshConnection;
            case "docker":
                DockerConnection dockerConnection = new DockerConnection();
                dockerConnection.initialize(properties);
                return dockerConnection;
            default:
                throw new PluginConfigurationException("Plugin ansible is not properly configured, connection_type [" + connectionType + "] is not supported");
        }
    }
}
