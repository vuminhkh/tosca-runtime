package com.mkv.tosca.sdk;

import java.util.Map;

import com.google.common.collect.Maps;

public abstract class AbstractRuntimeType {

    protected Map<String, Object> properties = Maps.newHashMap();

    protected Map<String, String> attributes = Maps.newHashMap();

    protected String recipeLocalPath;

    protected String csarName;

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
        Object value = this.properties.get(propertyName);
        return value != null ? String.valueOf(this.properties.get(propertyName)) : null;
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
