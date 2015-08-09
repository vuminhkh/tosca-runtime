package com.mkv.tosca.sdk;

import java.util.Map;

import com.google.common.collect.Maps;

public abstract class AbstractRuntimeType {

    protected Map<String, String> properties = Maps.newHashMap();

    protected Map<String, String> attributes = Maps.newHashMap();

    protected String recipeLocalPath;

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    protected String getProperty(String propertyName) {
        return this.properties.get(propertyName);
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
}