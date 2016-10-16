package com.toscaruntime.plugins.script;

import com.github.dockerjava.core.DockerClientConfig;
import com.toscaruntime.artifact.Connection;
import com.toscaruntime.artifact.Executor;
import com.toscaruntime.artifact.ExecutorConfiguration;
import com.toscaruntime.common.nodes.DockerContainer;
import com.toscaruntime.constant.ToscaInterfaceConstant;
import com.toscaruntime.plugins.script.shell.ShellExecutor;
import com.toscaruntime.plugins.script.util.ScriptUtil;
import com.toscaruntime.sdk.AbstractPluginHook;
import com.toscaruntime.sdk.Deployment;
import com.toscaruntime.util.DockerConnection;
import com.toscaruntime.util.PropertyUtil;
import com.toscaruntime.util.SSHConnection;
import tosca.nodes.Compute;
import tosca.nodes.Root;

import java.util.Map;

public class ScriptPluginHook extends AbstractPluginHook {

    private Map<String, Map<String, Object>> pluginProperties;

    @Override
    public void postConstruct(Deployment deployment, Map<String, Map<String, Object>> pluginProperties, Map<String, Object> bootstrapContext) {
        this.pluginProperties = pluginProperties;
        deployment.registerArtifactExecutor("tosca.artifacts.Implementation.Bash", ShellExecutor.class);
    }

    @Override
    public void postNodeInitialLoad(Root node) {
        if (node instanceof Compute) {
            registerExecutor((Compute) node);
        }
    }

    private void registerExecutor(Compute node) {
        Map<String, Object> executorProperties = PropertyUtil.getPluginConfiguration("script", this.pluginProperties, node.getProperties());
        String connectionType = PropertyUtil.getPropertyAsString(executorProperties, "connection_type");
        Class<? extends Connection> connectionClass = ScriptUtil.getConnectionType(node, connectionType);
        if (connectionClass == SSHConnection.class) {
            if (node instanceof com.toscaruntime.common.nodes.Compute) {
                executorProperties.put(Connection.TARGET, ((com.toscaruntime.common.nodes.Compute) node).getIpAddress());
            } else {
                executorProperties.put(Connection.TARGET, node.getAttribute("ip_address"));
            }
            executorProperties.put(Executor.LOCAL_RECIPE_LOCATION_KEY, node.getConfig().getArtifactsPath().toString());
            node.registerExecutor(new ExecutorConfiguration(ShellExecutor.class, connectionClass, executorProperties));
        } else if (connectionClass == DockerConnection.class) {
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
            node.registerExecutor(new ExecutorConfiguration(ShellExecutor.class, DockerConnection.class, executorProperties));
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
