package com.toscaruntime.sdk.model;

import com.toscaruntime.constant.InstanceState;
import com.toscaruntime.exception.deployment.configuration.IllegalFunctionException;
import com.toscaruntime.util.CodeGeneratorUtil;
import com.toscaruntime.util.FunctionUtil;
import com.toscaruntime.util.PropertyUtil;
import org.apache.commons.lang.StringUtils;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractRuntimeType {
    /**
     * Hold operation inputs : operation name to key to value
     */
    protected Map<String, Map<String, OperationInputDefinition>> operationInputs = new HashMap<>();

    /**
     * Hold last operation outputs : operation name to key to value
     */
    protected Map<String, Map<String, Object>> operationOutputs = new HashMap<>();

    protected DeploymentConfig config;

    protected String state = InstanceState.INITIAL;

    protected Map<String, Object> properties = new HashMap<>();

    protected Map<String, Object> attributes = new HashMap<>();

    protected Map<String, AttributeDefinition> attributeDefinitions = new HashMap<>();

    protected Map<String, String> deploymentArtifacts = new HashMap<>();

    public String getState() {
        return state;
    }

    /**
     * Method is called to notify plugin hooks, before entering an operation
     *
     * @param interfaceName name of the interface
     * @param operationName name of the operation
     */
    public abstract void executePluginsHooksBeforeOperation(String interfaceName, String operationName) throws Throwable;


    /**
     * Method is called to notify plugin hooks, after execution of an operation with success
     *
     * @param interfaceName name of the interface
     * @param operationName name of the operation
     */
    public abstract void executePluginsHooksAfterOperation(String interfaceName, String operationName) throws Throwable;

    /**
     * This method is called to set the state of the instance, it will trigger the persistence of the state
     *
     * @param state new state to persist
     */
    public abstract void setState(String state);

    /**
     * This method is called to set the operation outputs of the instance, it will trigger the persistence of those outputs
     *
     * @param interfaceName interface name
     * @param operationName operation name
     * @param outputs       outputs of the operation
     */
    public abstract void setOperationOutputs(String interfaceName, String operationName, Map<String, Object> outputs);

    /**
     * This method is called to set the attribute of the instance, it will trigger the persistence of the attribute
     *
     * @param key   key of the attribute
     * @param value new value of the attribute
     */
    public abstract void setAttribute(String key, Object value);

    /**
     * This method is called to remove the attribute of the instance, it will trigger the deletion in the persistence layer
     *
     * @param key key of the attribute
     */
    public abstract void removeAttribute(String key);

    /**
     * This method is called at the initialization of the instance to restore the instance's state
     */
    public abstract void initialLoad();

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    /**
     * Retrieve a copy of all instance attributes. Do not modify the returned map as it will not be persisted, instead using setAttribute or removeAttribute method.
     *
     * @return all instance attributes
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public Object getProperty(String propertyName) {
        return PropertyUtil.getProperty(this.properties, propertyName, null);
    }

    public String getPropertyAsString(String propertyName) {
        return PropertyUtil.getPropertyAsString(this.properties, propertyName);
    }

    public String getPropertyAsString(String propertyName, String defaultValue) {
        return PropertyUtil.getPropertyAsString(this.properties, propertyName, defaultValue);
    }

    public String getMandatoryPropertyAsString(String propertyName) {
        return PropertyUtil.getMandatoryPropertyAsString(this.properties, propertyName);
    }

    public String getAttributeAsString(String attributeName) {
        String attributeValue = PropertyUtil.getPropertyAsString(attributes, attributeName);
        if (StringUtils.isEmpty(attributeValue)) {
            return getPropertyAsString(attributeName);
        } else {
            return attributeValue;
        }
    }

    public Object getAttribute(String attributeName) {
        Object attributeValue = PropertyUtil.getProperty(attributes, attributeName, null);
        if (attributeValue == null) {
            return getProperty(attributeName);
        } else {
            return attributeValue;
        }
    }

    protected Object getInput(String inputName) {
        return PropertyUtil.getProperty(this.config.getInputs(), inputName, null);
    }

    protected Object getOperationOutput(String interfaceName, String operationName, String outputName) {
        String methodName = CodeGeneratorUtil.getGeneratedMethodName(interfaceName, operationName);
        Map<String, Object> outputs = operationOutputs.get(methodName);
        return outputs != null ? outputs.get(outputName) : null;
    }

    public DeploymentConfig getConfig() {
        return config;
    }

    public void setConfig(DeploymentConfig config) {
        this.config = config;
    }

    public String evaluateCompositeFunction(String functionName, Object... memberValue) {
        if ("concat".equals(functionName)) {
            return FunctionUtil.concat(memberValue);
        } else {
            throw new IllegalFunctionException("Function " + functionName + " is not supported");
        }
    }

    public void refreshAttributes() {
        for (Map.Entry<String, AttributeDefinition> attributeDefinitionEntry : attributeDefinitions.entrySet()) {
            setAttribute(attributeDefinitionEntry.getKey(), attributeDefinitionEntry.getValue().evaluateAttribute());
        }
    }

    public Map<String, String> getDeploymentArtifacts() {
        return deploymentArtifacts;
    }

    public void setDeploymentArtifacts(Map<String, String> deploymentArtifacts) {
        this.deploymentArtifacts = deploymentArtifacts;
    }

    public Map<String, Map<String, OperationInputDefinition>> getOperationInputs() {
        return operationInputs;
    }

    public Map<String, Map<String, Object>> getOperationOutputs() {
        return operationOutputs;
    }

    public Map<String, AttributeDefinition> getAttributeDefinitions() {
        return attributeDefinitions;
    }
}
