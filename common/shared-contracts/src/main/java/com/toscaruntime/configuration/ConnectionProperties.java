package com.toscaruntime.configuration;

import com.toscaruntime.util.ComparatorUtil;

import java.util.Map;

public class ConnectionProperties {

    private Map<String, Object> properties;

    public ConnectionProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    @Override
    public int hashCode() {
        return ComparatorUtil.hashCode(properties);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ConnectionProperties && ComparatorUtil.equals(properties, ((ConnectionProperties) obj).properties);
    }
}
