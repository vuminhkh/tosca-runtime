package com.mkv.tosca.sdk;

import java.util.Map;

import com.google.common.collect.Maps;
import com.mkv.util.PropertyUtil;

public abstract class AbstractRuntimeType {

    protected Map<String, Object> properties = Maps.newHashMap();

    protected Map<String, String> attributes = Maps.newHashMap();

    protected DeploymentConfig config;

    protected String state;

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

    protected String getAttribute(String attributeName) {
        return this.attributes.get(attributeName);
    }

    public DeploymentConfig getConfig() {
        return config;
    }

    public void setConfig(DeploymentConfig config) {
        this.config = config;
    }
}
