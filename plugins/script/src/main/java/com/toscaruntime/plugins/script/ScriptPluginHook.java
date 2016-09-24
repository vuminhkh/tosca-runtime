package com.toscaruntime.plugins.script;

import com.toscaruntime.artifact.Executor;
import com.toscaruntime.artifact.ExecutorConfiguration;
import com.toscaruntime.common.nodes.DockerContainer;
import com.toscaruntime.common.nodes.LinuxCompute;
import com.toscaruntime.constant.ToscaInterfaceConstant;
import com.toscaruntime.plugins.script.bash.BashExecutor;
import com.toscaruntime.sdk.Deployment;
import com.toscaruntime.sdk.PluginHook;
import com.toscaruntime.util.DockerConnection;
import com.toscaruntime.util.PropertyUtil;
import com.toscaruntime.util.SSHConnection;
import org.apache.commons.lang.StringUtils;
import tosca.nodes.Root;

import java.util.HashMap;
import java.util.Map;

public class ScriptPluginHook implements PluginHook {

    private Map<String, Map<String, Object>> pluginProperties;

    @Override
    public void postConstruct(Deployment deployment, Map<String, Map<String, Object>> pluginProperties, Map<String, Object> bootstrapContext) {
        this.pluginProperties = pluginProperties;
        deployment.registerArtifactExecutor("tosca.artifacts.Implementation.Bash", BashExecutor.class);
    }

    @Override
    public void preExecuteNodeOperation(Root node, String interfaceName, String operationName) throws Throwable {
    }

    @Override
    public void postExecuteNodeOperation(Root node, String interfaceName, String operationName) throws Throwable {
        if (ToscaInterfaceConstant.NODE_STANDARD_INTERFACE.equals(interfaceName) && ToscaInterfaceConstant.START_OPERATION.equals(operationName)) {
            Map<String, Object> nodeProperties = new HashMap<>();
            if (this.pluginProperties != null) {
                // Get properties from plugin first so that properties from node can override
                nodeProperties.putAll(this.pluginProperties);
            }
            Map<String, Object> scriptPluginConfigurationFromNode = (Map<String, Object>) PropertyUtil.getProperty(node.getProperties(), "plugins.script.configuration");
            if (scriptPluginConfigurationFromNode != null) {
                // Get properties from node for the plugin
                nodeProperties.putAll(scriptPluginConfigurationFromNode);
            }
            Map<String, Object> executorProperties = new HashMap<>();
            // Give every raw properties here
            executorProperties.put("configuration", nodeProperties);
            // Post start operation computes should be available for artifact execution
            if (node instanceof LinuxCompute) {
                LinuxCompute linuxCompute = (LinuxCompute) node;
                executorProperties.put("ip", linuxCompute.getIpAddress());
                executorProperties.put("port", PropertyUtil.getMandatoryPropertyAsString(nodeProperties, "ssh_port"));
                executorProperties.put("user", PropertyUtil.getMandatoryPropertyAsString(nodeProperties, "login"));
                executorProperties.put(Executor.LOCAL_RECIPE_LOCATION_KEY, node.getConfig().getArtifactsPath().toString());
                String pemPath = PropertyUtil.getPropertyAsString(nodeProperties, "key_path");
                String pemContent = PropertyUtil.getPropertyAsString(nodeProperties, "key_content");
                if (StringUtils.isNotBlank(pemContent)) {
                    executorProperties.put("pem_content", pemContent);
                }
                if (StringUtils.isNotBlank(pemPath)) {
                    executorProperties.put("pem_path", pemPath);
                }
                // A Linux VM needs SSH connection to execute bash script
                ((LinuxCompute) node).registerExecutor(new ExecutorConfiguration(BashExecutor.class, SSHConnection.class, executorProperties));
            } else if (node instanceof DockerContainer) {
                DockerContainer dockerContainer = (DockerContainer) node;
                executorProperties.put("docker_url", dockerContainer.getDockerURL());
                executorProperties.put("cert_path", dockerContainer.getDockerCertificatePath());
                executorProperties.put("container_id", dockerContainer.getContainerId());
                executorProperties.put(Executor.LOCAL_RECIPE_LOCATION_KEY, node.getConfig().getArtifactsPath().toString());
                // A docker container needs a Docker connection
                ((DockerContainer) node).registerExecutor(new ExecutorConfiguration(BashExecutor.class, DockerConnection.class, executorProperties));
            }
        }
    }

    @Override
    public void preExecuteRelationshipOperation(tosca.relationships.Root relationship, String interfaceName, String operationName) throws Throwable {
    }

    @Override
    public void postExecuteRelationshipOperation(tosca.relationships.Root relationship, String interfaceName, String operationName) throws Throwable {
    }
}
