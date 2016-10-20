package com.toscaruntime.ansible;

import com.github.dockerjava.core.DockerClientConfig;
import com.toscaruntime.ansible.connection.AnsibleConnection;
import com.toscaruntime.ansible.connection.AnsibleDockerConnection;
import com.toscaruntime.ansible.connection.AnsibleSSHConnection;
import com.toscaruntime.ansible.executor.AnsiblePlaybookExecutor;
import com.toscaruntime.ansible.util.AnsibleUtil;
import com.toscaruntime.artifact.Connection;
import com.toscaruntime.artifact.Executor;
import com.toscaruntime.artifact.ExecutorConfiguration;
import com.toscaruntime.common.nodes.DockerContainer;
import com.toscaruntime.configuration.ConnectionRegistry;
import com.toscaruntime.constant.ToscaInterfaceConstant;
import com.toscaruntime.exception.deployment.plugins.PluginConfigurationException;
import com.toscaruntime.sdk.AbstractPluginHook;
import com.toscaruntime.sdk.Deployment;
import com.toscaruntime.util.PropertyUtil;
import tosca.nodes.Compute;
import tosca.nodes.Root;

import java.util.Map;
import java.util.stream.Collectors;

public class AnsiblePluginHook extends AbstractPluginHook {

    private Map<String, Map<String, Object>> pluginProperties;

    private ConnectionRegistry<Connection> controlMachineConnectionRegistry;

    @Override
    public void postConstruct(Deployment deployment, Map<String, Map<String, Object>> pluginProperties, Map<String, Object> bootstrapContext) {
        this.pluginProperties = pluginProperties;
        Map<String, Map<String, Object>> controlMachineProperties = this.pluginProperties.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> {
            Object controlMachineProperty = PropertyUtil.getMandatoryProperty(entry.getValue(), "control_machine");
            if (!(controlMachineProperty instanceof Map)) {
                throw new PluginConfigurationException("control_machine property value must be a complex object");
            }
            return (Map<String, Object>) controlMachineProperty;
        }));
        this.controlMachineConnectionRegistry = new ConnectionRegistry<>(controlMachineProperties, bootstrapContext, new AnsibleControlMachineConnectionFactory());
        deployment.registerArtifactExecutor("com.toscaruntime.artifacts.Implementation.AnsiblePlaybook", AnsiblePlaybookExecutor.class);
    }

    @Override
    public void postNodeInitialLoad(Root node) {
        if (node instanceof Compute) {
            registerExecutor((Compute) node);
        }
    }

    private void registerExecutor(Compute node) {
        String configuredTarget = PropertyUtil.getPluginConfigurationTarget("ansible", node.getProperties());
        Map<String, Object> executorProperties = PropertyUtil.getPluginConfiguration("ansible", this.pluginProperties, node.getProperties());
        Object controlMachineProperty = PropertyUtil.getProperty(executorProperties, "control_machine");
        if (!(controlMachineProperty instanceof Map)) {
            throw new PluginConfigurationException("On node [" + node.getName() + "] control_machine property value must be a complex object for the plugin ansible");
        }
        Connection controlMachineConnection = this.controlMachineConnectionRegistry.getConnection(configuredTarget, (Map<String, Object>) controlMachineProperty);
        executorProperties.put(AnsibleConnection.CONTROL_MACHINE_CONNECTION, controlMachineConnection);
        String connectionType = PropertyUtil.getPropertyAsString(executorProperties, "connection_type");
        Class<? extends Connection> connectionClass = AnsibleUtil.getConnectionType(node, connectionType);
        if (connectionClass == AnsibleSSHConnection.class) {
            if (node instanceof com.toscaruntime.common.nodes.Compute) {
                executorProperties.put(Connection.TARGET, ((com.toscaruntime.common.nodes.Compute) node).getIpAddress());
            } else {
                executorProperties.put(Connection.TARGET, node.getAttribute("ip_address"));
            }
            executorProperties.put(Executor.LOCAL_RECIPE_LOCATION_KEY, node.getConfig().getArtifactsPath().toString());
            node.registerExecutor(new ExecutorConfiguration(AnsiblePlaybookExecutor.class, connectionClass, executorProperties));
        } else if (connectionClass == AnsibleDockerConnection.class) {
            executorProperties.put(Executor.LOCAL_RECIPE_LOCATION_KEY, node.getConfig().getArtifactsPath().toString());
            if (node instanceof DockerContainer) {
                DockerContainer dockerContainer = (DockerContainer) node;
                executorProperties.put(DockerClientConfig.DOCKER_HOST, dockerContainer.getDockerHost());
                executorProperties.put(DockerClientConfig.DOCKER_CERT_PATH, dockerContainer.getDockerCertificatePath());
                executorProperties.put(DockerClientConfig.DOCKER_TLS_VERIFY, dockerContainer.getTlsVerify());
                executorProperties.put(Connection.TARGET, dockerContainer.getContainerId());
            } else {
                executorProperties.put(DockerClientConfig.DOCKER_HOST, node.getAttribute(DockerClientConfig.DOCKER_HOST));
                executorProperties.put(DockerClientConfig.DOCKER_CERT_PATH, node.getAttribute(DockerClientConfig.DOCKER_CERT_PATH));
                executorProperties.put(DockerClientConfig.DOCKER_TLS_VERIFY, node.getAttribute(DockerClientConfig.DOCKER_TLS_VERIFY));
                executorProperties.put(Connection.TARGET, node.getAttribute("provider_resource_id"));
            }
            node.registerExecutor(new ExecutorConfiguration(AnsiblePlaybookExecutor.class, connectionClass, executorProperties));
        }
    }

    @Override
    public void postExecuteNodeOperation(Root node, String interfaceName, String operationName) {
        if (ToscaInterfaceConstant.NODE_STANDARD_INTERFACE.equals(interfaceName) && ToscaInterfaceConstant.START_OPERATION.equals(operationName) && node instanceof Compute) {
            registerExecutor((Compute) node);
        }
    }

    @Override
    public void preExecuteNodeOperation(Root node, String interfaceName, String operationName) {
        if (ToscaInterfaceConstant.NODE_STANDARD_INTERFACE.equals(interfaceName) && ToscaInterfaceConstant.STOP_OPERATION.equals(operationName) && node instanceof Compute) {
            ((Compute) node).unregisterExecutors();
        }
    }
}
