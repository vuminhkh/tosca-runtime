package com.toscaruntime.sdk.model;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.toscaruntime.exception.IllegalFunctionException;
import com.toscaruntime.sdk.Deployment;
import com.toscaruntime.util.CodeGeneratorUtil;
import com.toscaruntime.util.FunctionUtil;
import com.toscaruntime.util.PropertyUtil;

public abstract class AbstractRuntimeType {

    /**
     * Hold last operation outputs : operation name to key to value
     */
    protected Map<String, Map<String, String>> operationOutputs = new HashMap<>();

    protected Deployment deployment;

    protected DeploymentConfig config;

    protected String state = "initial";

    protected Map<String, Object> properties = new HashMap<>();

    protected Map<String, Object> attributes = new HashMap<>();

    protected Map<String, AttributeDefinition> attributeDefinitions = new HashMap<>();

    protected Map<String, String> deploymentArtifacts = new HashMap<>();

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
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

    protected String getOperationOutput(String interfaceName, String operationName, String outputName) {
        String methodName = CodeGeneratorUtil.getGeneratedMethodName(interfaceName, operationName);
        Map<String, String> outputs = operationOutputs.get(methodName);
        if (outputs != null) {
            String output = outputs.get(outputName);
            return output != null ? output : "";
        } else {
            return "";
        }
    }

    public DeploymentConfig getConfig() {
        return config;
    }

    public void setConfig(DeploymentConfig config) {
        this.config = config;
    }

    public void setAttribute(String key, Object value) {
        getAttributes().put(key, value);
    }

    public void removeAttribute(String key) {
        getAttributes().remove(key);
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

    public Deployment getDeployment() {
        return deployment;
    }

    public void setDeployment(Deployment deployment) {
        this.deployment = deployment;
    }
}
