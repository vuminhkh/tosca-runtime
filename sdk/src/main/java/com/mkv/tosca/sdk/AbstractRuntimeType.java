package com.mkv.tosca.sdk;

import java.util.Map;

import com.google.common.collect.Maps;
import com.mkv.exception.NonRecoverableException;
import com.mkv.util.PropertyUtil;

public abstract class AbstractRuntimeType {

    protected Map<String, Object> properties = Maps.newHashMap();

    protected Map<String, String> attributes = Maps.newHashMap();

    protected String recipeLocalPath;

    protected String csarName;

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

    protected String getProperty(String propertyName, String defaultValue) {
        String value = PropertyUtil.getPropertyAsString(this.properties, propertyName);
        return value != null ? value : defaultValue;
    }

    protected String getMandatoryProperty(String propertyName) {
        String value = PropertyUtil.getPropertyAsString(this.properties, propertyName);
        if (value == null) {
            throw new NonRecoverableException("Property <" + propertyName + "> is required but missing");
        } else {
            return value;
        }
    }

    protected String getAttribute(String attributeName) {
        return this.attributes.get(attributeName);
    }

    public String getRecipeLocalPath() {
        return recipeLocalPath;
    }

    public void setRecipeLocalPath(String recipeLocalPath) {
        this.recipeLocalPath = recipeLocalPath;
    }

    public String getCsarName() {
        return csarName;
    }

    public void setCsarName(String csarName) {
        this.csarName = csarName;
    }
}
