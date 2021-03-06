package tosca.nodes;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.toscaruntime.exception.UnexpectedException;
import com.toscaruntime.exception.deployment.configuration.IllegalFunctionException;
import com.toscaruntime.exception.deployment.persistence.DeploymentPersistenceException;
import com.toscaruntime.sdk.model.AbstractRuntimeType;
import com.toscaruntime.sdk.model.DeploymentNode;
import com.toscaruntime.sdk.model.DeploymentRelationshipNode;
import com.toscaruntime.sdk.model.OperationInputDefinition;
import com.toscaruntime.sdk.util.OperationInputUtil;
import com.toscaruntime.util.CodeGeneratorUtil;
import com.toscaruntime.util.FunctionUtil;
import com.toscaruntime.util.JSONUtil;
import com.toscaruntime.util.PropertyUtil;

public abstract class Root extends AbstractRuntimeType {

    private int index;

    private String name;

    private DeploymentNode node;
    /**
     * The parent node is the one the node is attached to or is hosted on
     */
    private Root parent;

    /**
     * The direct host node is the one the node is hosted on
     */
    private Root host;

    private Map<String, Map<String, Object>> capabilitiesProperties;

    private Set<DeploymentRelationshipNode> preConfiguredRelationshipNodes = Collections.synchronizedSet(new HashSet<>());

    private Set<DeploymentRelationshipNode> postConfiguredRelationshipNodes = Collections.synchronizedSet(new HashSet<>());

    public Object getCapabilityProperty(String capabilityName, String propertyName) {
        if (capabilitiesProperties.containsKey(capabilityName)) {
            return PropertyUtil.getProperty(capabilitiesProperties.get(capabilityName), propertyName);
        } else {
            return null;
        }
    }

    public Map<String, Map<String, Object>> getCapabilitiesProperties() {
        return capabilitiesProperties;
    }

    public void setCapabilitiesProperties(Map<String, Map<String, Object>> capabilitiesProperties) {
        this.capabilitiesProperties = capabilitiesProperties;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public DeploymentNode getNode() {
        return node;
    }

    public void setNode(DeploymentNode node) {
        this.node = node;
    }

    @Override
    public void initialLoad() {
        Map<String, String> rawAttributes = deployment.getDeploymentPersister().syncGetAttributes(getId());
        for (Map.Entry<String, String> rawAttributeEntry : rawAttributes.entrySet()) {
            try {
                getAttributes().put(rawAttributeEntry.getKey(), JSONUtil.toObject(rawAttributeEntry.getValue()));
            } catch (IOException e) {
                throw new DeploymentPersistenceException("Cannot read as json from persistence attribute " + rawAttributeEntry.getKey() + " of node instance " + getId(), e);
            }
        }
        List<String> outputInterfaces = deployment.getDeploymentPersister().syncGetOutputInterfaces(getId());
        for (String interfaceName : outputInterfaces) {
            List<String> operationNames = deployment.getDeploymentPersister().syncGetOutputOperations(getId(), interfaceName);
            for (String operationName : operationNames) {
                Map<String, String> outputs = deployment.getDeploymentPersister().syncGetOutputs(getId(), interfaceName, operationName);
                operationOutputs.put(CodeGeneratorUtil.getGeneratedMethodName(interfaceName, operationName), outputs);
            }
        }
        this.state = deployment.getDeploymentPersister().syncGetInstanceState(getId());
    }

    @Override
    public void setState(String newState) {
        if (!newState.equals(this.state)) {
            deployment.getDeploymentPersister().syncSaveInstanceState(getId(), newState);
            this.state = newState;
        }
    }

    @Override
    public void setAttribute(String key, Object newValue) {
        Object oldValue = getAttributes().get(key);
        if (newValue == null) {
            removeAttribute(key);
        } else if (!newValue.equals(oldValue)) {
            try {
                deployment.getDeploymentPersister().syncSaveInstanceAttribute(getId(), key, JSONUtil.toString(newValue));
            } catch (Exception e) {
                throw new DeploymentPersistenceException("Cannot persist attribute " + key + " of node instance " + getId(), e);
            }
            getAttributes().put(key, newValue);
        }
    }

    @Override
    public void removeAttribute(String key) {
        deployment.getDeploymentPersister().syncDeleteInstanceAttribute(getId(), key);
        getAttributes().remove(key);
    }

    @Override
    public void setOperationOutputs(String interfaceName, String operationName, Map<String, String> outputs) {
        deployment.getDeploymentPersister().syncSaveInstanceOutputs(getId(), interfaceName, operationName, outputs);
        operationOutputs.put(CodeGeneratorUtil.getGeneratedMethodName(interfaceName, operationName), outputs);
    }

    /**
     * Id of a node is generated based on its index within its parent and its parent index within its grandparent etc ...
     * For example: A war hosted on a tomcat which is hosted on a compute will have as id war_1_1_1 or war_2_1_1 (if the compute is scaled with 2 instances)
     *
     * @return generated id of the node
     */
    public String getId() {
        String id = getName();
        LinkedList<Integer> indexQueue = new LinkedList<>();
        indexQueue.push(index);
        Root currentParent = getParent();
        while (currentParent != null) {
            indexQueue.push(currentParent.getIndex());
            currentParent = currentParent.getParent();
        }
        for (Integer index : indexQueue) {
            id += "_" + index;
        }
        return id;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public Compute getComputableHost() {
        if (host == null) {
            if (this instanceof Compute) {
                return (Compute) this;
            } else {
                return null;
            }
        }
        Root currentHost = host;
        while (currentHost.getHost() != null) {
            currentHost = currentHost.getHost();
        }
        if (currentHost instanceof Compute) {
            return (Compute) currentHost;
        } else {
            return null;
        }
    }

    public Root getParent() {
        return parent;
    }

    public void setParent(Root parent) {
        this.parent = parent;
    }

    public Root getHost() {
        return host;
    }

    public void setHost(Root host) {
        this.host = host;
    }

    public Set<DeploymentRelationshipNode> getPreConfiguredRelationshipNodes() {
        return preConfiguredRelationshipNodes;
    }

    public Set<DeploymentRelationshipNode> getPostConfiguredRelationshipNodes() {
        return postConfiguredRelationshipNodes;
    }

    protected Map<String, String> executeOperation(String operationName, String operationArtifactPath) {
        Compute host = getComputableHost();
        if (host == null) {
            // This error should be avoided by validating the recipe in compilation phase
            throw new UnexpectedException("Non hosted node cannot have operation");
        }
        Map<String, OperationInputDefinition> inputDefinitions = operationInputs.get(operationName);
        Map<String, Object> inputs = OperationInputUtil.evaluateInputDefinitions(inputDefinitions);
        inputs.put("NODE", getName());
        inputs.put("INSTANCE", getId());
        inputs.put("INSTANCES", OperationInputUtil.makeInstancesVariable(getNode().getInstances()));
        inputs.put("HOST", getHost().getName());
        for (Root sibling : getNode().getInstances()) {
            // This will inject also other instances input value
            Map<String, OperationInputDefinition> siblingInputDefinitions = sibling.getOperationInputs().get(operationName);
            inputs.putAll(OperationInputUtil.evaluateInputDefinitions(sibling.getId(), siblingInputDefinitions));
        }
        return host.execute(getId(), operationArtifactPath, inputs, getDeploymentArtifacts());
    }

    public void create() {

    }

    public void configure() {

    }

    public void start() {

    }

    public void stop() {

    }

    public void delete() {
    }

    private String functionToString(String functionName, String... paths) {
        StringBuilder buffer = new StringBuilder(functionName).append("[ ");
        for (String path : paths) {
            buffer.append(path).append(",");
        }
        buffer.setLength(buffer.length() - 1);
        buffer.append("]");
        return buffer.toString();
    }

    public Object evaluateFunction(String functionName, String... paths) {
        if (paths.length == 0) {
            throw new IllegalFunctionException("Function " + functionName + " path is empty");
        }
        if ("get_input".equals(functionName)) {
            return getInput(paths[0]);
        }
        String entity = paths[0];
        Object value;
        switch (entity) {
            case "HOST":
                if (getHost() == null) {
                    throw new IllegalFunctionException("Cannot " + functionToString(functionName, paths) + " as this node does not have a direct host");
                }
                return getHost().evaluateFunction(functionName, FunctionUtil.setEntityToSelf(paths));
            case "SELF":
                switch (functionName) {
                    case "get_property":
                        if (paths.length == 2) {
                            value = getProperty(paths[1]);
                        } else if (paths.length == 3) {
                            value = getCapabilityProperty(paths[1], paths[2]);
                        } else {
                            throw new IllegalFunctionException("get_property must be followed by entity and the property name (2 arguments), or entity then requirement/capability name and property name (3 arguments)");
                        }
                        break;
                    case "get_attribute":
                        value = getAttribute(paths[1]);
                        break;
                    case "get_operation_output":
                        value = getOperationOutput(paths[1], paths[2], paths[3]);
                        break;
                    default:
                        throw new IllegalFunctionException("Function " + functionName + " is not supported on SELF entity");
                }
                break;
            default:
                throw new IllegalFunctionException("Entity " + entity + " is not supported");
        }
        if (value == null) {
            if (getHost() != null) {
                return getHost().evaluateFunction(functionName, paths);
            } else {
                return "";
            }
        } else {
            return value;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Root root = (Root) o;
        return getId().equals(root.getId());
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

    @Override
    public String toString() {
        return "NodeInstance{" +
                "id='" + getId() + '\'' +
                '}';
    }
}
