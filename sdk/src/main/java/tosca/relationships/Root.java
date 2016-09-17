package tosca.relationships;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.toscaruntime.exception.UnexpectedException;
import com.toscaruntime.exception.deployment.configuration.IllegalFunctionException;
import com.toscaruntime.exception.deployment.persistence.DeploymentPersistenceException;
import com.toscaruntime.sdk.PluginHook;
import com.toscaruntime.sdk.model.AbstractRuntimeType;
import com.toscaruntime.sdk.model.DeploymentRelationshipNode;
import com.toscaruntime.sdk.model.OperationInputDefinition;
import com.toscaruntime.sdk.util.OperationInputUtil;
import com.toscaruntime.util.CodeGeneratorUtil;
import com.toscaruntime.util.FunctionUtil;
import com.toscaruntime.util.JSONUtil;
import tosca.nodes.Compute;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class Root extends AbstractRuntimeType {

    private tosca.nodes.Root source;

    private tosca.nodes.Root target;

    private DeploymentRelationshipNode node;

    public tosca.nodes.Root getSource() {
        return source;
    }

    public tosca.nodes.Root getTarget() {
        return target;
    }

    public DeploymentRelationshipNode getNode() {
        return node;
    }

    protected Map<String, String> executeOperation(String operationName, String operationArtifactPath, String artifactType) {
        Map<String, OperationInputDefinition> inputDefinitions = operationInputs.get(operationName);
        Map<String, Object> inputs = OperationInputUtil.evaluateInputDefinitions(inputDefinitions);
        inputs.put("TARGET_NODE", getTarget().getName());
        inputs.put("TARGET_INSTANCE", getTarget().getId());
        inputs.put("TARGET_INSTANCE", getTarget().getId());
        inputs.put("TARGET_INSTANCES", OperationInputUtil.makeInstancesVariable(getTarget().getNode().getInstances()));
        inputs.put("SOURCE_NODE", getSource().getName());
        inputs.put("SOURCE_INSTANCE", getSource().getId());
        inputs.put("SOURCE_INSTANCE", getSource().getId());
        inputs.put("SOURCE_INSTANCES", OperationInputUtil.makeInstancesVariable(getSource().getNode().getInstances()));
        for (Root sibling : getNode().getRelationshipInstances()) {
            // Inject also other inputs from other instances
            Map<String, OperationInputDefinition> siblingInputDefinitions = sibling.getOperationInputs().get(operationName);
            inputs.putAll(OperationInputUtil.evaluateInputDefinitions(sibling.getTarget().getId(), siblingInputDefinitions));
        }
        switch (operationName) {
            case "pre_configure_source":
            case "post_configure_source":
            case "add_target":
            case "target_changed":
            case "remove_target":
                return executeSourceOperation(operationName, operationArtifactPath, artifactType, inputs);
            case "pre_configure_target":
            case "post_configure_target":
            case "add_source":
            case "source_changed":
            case "remove_source":
                return executeTargetOperation(operationName, operationArtifactPath, artifactType, inputs);
            default:
                if (operationName.endsWith("_source")) {
                    return executeSourceOperation(operationName, operationArtifactPath, artifactType, inputs);
                } else if (operationName.endsWith("_target")) {
                    return executeTargetOperation(operationName, operationArtifactPath, artifactType, inputs);
                } else {
                    // This is unexpected as this kind of error should be detected in compilation phase
                    throw new UnexpectedException("Operation does not specify to be executed on source or target node (must be suffixed by _source or _target)");
                }
        }
    }

    protected Map<String, String> executeSourceOperation(String operationName, String operationArtifactPath, String artifactType, Map<String, Object> inputs) {
        Compute sourceHost = source.getComputableHost();
        if (sourceHost == null) {
            // This is unexpected as this kind of error should be detected in compilation phase
            throw new UnexpectedException("The relationship's source is not set or not hosted on a compute, operation cannot be executed");
        }
        Map<String, String> operationDeploymentArtifacts = new HashMap<>(getDeploymentArtifacts());
        operationDeploymentArtifacts.putAll(source.getDeploymentArtifacts());
        return sourceHost.execute(source.getId(), operationName, operationArtifactPath, artifactType, inputs, operationDeploymentArtifacts);
    }

    protected Map<String, String> executeTargetOperation(String operationName, String operationArtifactPath, String artifactType, Map<String, Object> inputs) {
        Compute targetHost = target.getComputableHost();
        if (targetHost == null) {
            // This is unexpected as this kind of error should be detected in compilation phase
            throw new UnexpectedException("The relationship's target is not hosted on a compute, operation cannot be executed");
        }
        Map<String, String> operationDeploymentArtifacts = new HashMap<>(getDeploymentArtifacts());
        operationDeploymentArtifacts.putAll(target.getDeploymentArtifacts());
        return targetHost.execute(target.getId(), operationName, operationArtifactPath, artifactType, inputs, operationDeploymentArtifacts);
    }

    public Object evaluateFunction(String functionName, String... paths) {
        String entity = paths[0];
        switch (entity) {
            case "SOURCE":
                return source.evaluateFunction(functionName, FunctionUtil.setEntityToSelf(paths));
            case "TARGET":
                return target.evaluateFunction(functionName, FunctionUtil.setEntityToSelf(paths));
            case "SELF":
                switch (functionName) {
                    case "get_property":
                        return getProperty(paths[1]);
                    case "get_attribute":
                        return getAttribute(paths[1]);
                    case "get_input":
                        return getInput(paths[1]);
                    case "get_operation_output":
                        return getOperationOutput(paths[1], paths[2], paths[3]);
                    default:
                        throw new IllegalFunctionException("Function " + functionName + " is not supported");
                }
            default:
                throw new IllegalFunctionException("Entity " + entity + " is not supported");
        }
    }

    public void setSource(tosca.nodes.Root source) {
        this.source = source;
    }

    public void setTarget(tosca.nodes.Root target) {
        this.target = target;
    }

    public void setNode(DeploymentRelationshipNode node) {
        this.node = node;
    }

    public void preConfigureSource() {
    }

    public void preConfigureTarget() {
    }

    public void postConfigureSource() {
    }

    public void postConfigureTarget() {
    }

    public void addTarget() {
    }

    public void addSource() {
    }

    public void removeSource() {
    }

    public void removeTarget() {
    }

    @Override
    public void executePluginsHooksBeforeOperation(String interfaceName, String operationName) throws Throwable {
        for (PluginHook pluginHook : config.getPluginHooks()) {
            pluginHook.preExecuteRelationshipOperation(this, interfaceName, operationName);
        }
    }

    @Override
    public void executePluginsHooksAfterOperation(String interfaceName, String operationName) throws Throwable {
        for (PluginHook pluginHook : config.getPluginHooks()) {
            pluginHook.postExecuteRelationshipOperation(this, interfaceName, operationName);
        }
    }

    @Override
    public void initialLoad() {
        Map<String, String> rawAttributes = config.getDeploymentPersister().syncGetRelationshipAttributes(getSource().getId(), getTarget().getId(), node.getRelationshipName());
        for (Map.Entry<String, String> rawAttributeEntry : rawAttributes.entrySet()) {
            try {
                getAttributes().put(rawAttributeEntry.getKey(), JSONUtil.toObject(rawAttributeEntry.getValue()));
            } catch (IOException e) {
                throw new DeploymentPersistenceException("Cannot read as json from persistence attribute " + rawAttributeEntry.getKey() + " of relationship instance " + this, e);
            }
        }
        List<String> outputInterfaces = config.getDeploymentPersister().syncGetRelationshipOutputInterfaces(getSource().getId(), getTarget().getId(), node.getRelationshipName());
        for (String interfaceName : outputInterfaces) {
            List<String> operationNames = config.getDeploymentPersister().syncGetRelationshipOutputOperations(getSource().getId(), getTarget().getId(), node.getRelationshipName(), interfaceName);
            for (String operationName : operationNames) {
                Map<String, String> outputs = config.getDeploymentPersister().syncGetRelationshipOutputs(getSource().getId(), getTarget().getId(), node.getRelationshipName(), interfaceName, operationName);
                operationOutputs.put(CodeGeneratorUtil.getGeneratedMethodName(interfaceName, operationName), outputs);
            }
        }
        this.state = config.getDeploymentPersister().syncGetRelationshipInstanceState(getSource().getId(), getTarget().getId(), node.getRelationshipName());
    }

    @Override
    public void setState(String state) {
        if (!state.equals(this.state)) {
            config.getDeploymentPersister().syncSaveRelationshipState(getSource().getId(), getTarget().getId(), node.getRelationshipName(), state);
            this.state = state;
        }
    }

    @Override
    public void setAttribute(String key, Object newValue) {
        Object oldValue = getAttributes().get(key);
        if (newValue == null) {
            removeAttribute(key);
        } else if (!newValue.equals(oldValue)) {
            try {
                config.getDeploymentPersister().syncSaveRelationshipAttribute(getSource().getId(), getTarget().getId(), node.getRelationshipName(), key, JSONUtil.toString(newValue));
            } catch (JsonProcessingException e) {
                throw new DeploymentPersistenceException("Cannot persist attribute " + key + " with value " + newValue + " of relationship instance from " + getSource().getId() + " to " + getTarget().getId() + " of type " + node.getRelationshipName(), e);
            }
            getAttributes().put(key, newValue);
            // Attribute of the relationship is copied to the node
            getSource().setAttribute(key, newValue);
            getTarget().setAttribute(key, newValue);
        }
    }

    @Override
    public void setOperationOutputs(String interfaceName, String operationName, Map<String, String> outputs) {
        config.getDeploymentPersister().syncSaveRelationshipOutputs(source.getId(), target.getId(), node.getRelationshipName(), interfaceName, operationName, outputs);
        operationOutputs.put(CodeGeneratorUtil.getGeneratedMethodName(interfaceName, operationName), outputs);
    }

    @Override
    public void removeAttribute(String key) {
        getSource().removeAttribute(key);
        getTarget().removeAttribute(key);
        config.getDeploymentPersister().syncDeleteRelationshipAttribute(source.getId(), target.getId(), node.getRelationshipName(), key);
        getAttributes().remove(key);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Root root = (Root) o;

        if (!source.equals(root.source)) return false;
        if (!target.equals(root.target)) return false;
        return node.getRelationshipType().equals(root.node.getRelationshipType());

    }

    @Override
    public int hashCode() {
        int result = source.hashCode();
        result = 31 * result + target.hashCode();
        result = 31 * result + node.getRelationshipType().hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Root{" +
                "source=" + source.getId() +
                ", target=" + target.getId() +
                ", type=" + node.getRelationshipName() +
                '}';
    }
}
