package com.toscaruntime.util;

import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;

public class PropertyUtil {

    public static String getMandatoryPropertyAsString(Map<String, ?> properties, String propertyName) {
        String value = PropertyUtil.getPropertyAsString(properties, propertyName);
        if (value == null) {
            throw new RuntimeException("Property <" + propertyName + "> is required but missing");
        } else {
            return value;
        }
    }

    public static String getPropertyAsString(Map<String, ?> properties, String propertyName) {
        Object value = properties.get(propertyName);
        return value != null ? String.valueOf(properties.get(propertyName)) : null;
    }

    public static String getPropertyAsString(Map<String, ?> properties, String propertyName, String defaultValue) {
        String value = getPropertyAsString(properties, propertyName);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        } else {
            return value;
        }
    }

    public static Properties toProperties(Map<String, ?> properties, String... ignoredKeys) {
        Properties javaProperties = new Properties();
        javaProperties.putAll(properties);
        for (String key : ignoredKeys) {
            javaProperties.remove(key);
        }
        return javaProperties;
    }
}
