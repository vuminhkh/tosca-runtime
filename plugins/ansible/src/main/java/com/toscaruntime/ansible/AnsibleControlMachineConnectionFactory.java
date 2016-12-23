package com.toscaruntime.ansible;

import com.toscaruntime.artifact.Connection;
import com.toscaruntime.configuration.ConnectionFactory;
import com.toscaruntime.connection.DockerConnection;
import com.toscaruntime.connection.LocalConnection;
import com.toscaruntime.connection.SSHConnection;
import com.toscaruntime.exception.deployment.plugins.PluginConfigurationException;
import com.toscaruntime.util.PropertyUtil;

import java.util.Map;

public class AnsibleControlMachineConnectionFactory implements ConnectionFactory<Connection> {

    @Override
    public Connection newConnection(Map<String, Object> properties, Map<String, Object> bootstrapContext, boolean multipleTargets) {
        String connectionType = PropertyUtil.getMandatoryPropertyAsString(properties, Connection.CONNECTION_TYPE);
        switch (connectionType) {
            case "local":
                LocalConnection localConnection = new LocalConnection();
                localConnection.initialize(properties);
                return localConnection;
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
