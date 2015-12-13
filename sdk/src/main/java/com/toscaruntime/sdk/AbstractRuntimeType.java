package com.toscaruntime.sdk;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.toscaruntime.exception.IllegalFunctionException;
import com.toscaruntime.util.CodeGeneratorUtil;
import com.toscaruntime.util.FunctionUtil;
import com.toscaruntime.util.PropertyUtil;

public abstract class AbstractRuntimeType {

    /**
     * Hold last operation outputs : operation name to key to value
     */
    protected Map<String, Map<String, String>> operationOutputs = new HashMap<>();

    protected Map<String, Object> properties = new HashMap<>();

    protected Map<String, String> attributes = new HashMap<>();

    protected DeploymentConfig config;

    protected String state = "initial";

    protected Map<String, AttributeDefinition> attributeDefinitions = new HashMap<>();

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

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    protected String getProperty(String propertyName) {
        return PropertyUtil.getPropertyAsString(this.properties, propertyName);
    }

    public String getProperty(String propertyName, String defaultValue) {
        String value = PropertyUtil.getPropertyAsString(this.properties, propertyName);
        return value != null ? value : defaultValue;
    }

    public String getMandatoryProperty(String propertyName) {
        return PropertyUtil.getMandatoryPropertyAsString(this.properties, propertyName);
    }

    public String getAttribute(String attributeName) {
        String attributeValue = this.attributes.get(attributeName);
        if (StringUtils.isEmpty(attributeValue)) {
            return getProperty(attributeName);
        } else {
            return attributeValue;
        }
    }

    protected String getInput(String inputName) {
        return PropertyUtil.getPropertyAsString(this.config.getInputs(), inputName);
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

    public void setAttribute(String key, String value) {
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
}
