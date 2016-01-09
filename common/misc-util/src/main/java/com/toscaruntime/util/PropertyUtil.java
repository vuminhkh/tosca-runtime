package com.toscaruntime.util;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.toscaruntime.exception.IllegalFunctionException;
import com.toscaruntime.exception.PropertyAccessException;

public class PropertyUtil {

    public static List<Object> toList(String propertyValue) {
        try {
            return JSONUtil.toList(propertyValue);
        } catch (IOException e) {
            throw new PropertyAccessException("Could not parse <" + propertyValue + "> as a list");
        }
    }

    public static Map<String, Object> toMap(String propertyValue) {
        try {
            return JSONUtil.toMap(propertyValue);
        } catch (IOException e) {
            throw new PropertyAccessException("Could not parse <" + propertyValue + "> as a map");
        }
    }

    public static String getMandatoryPropertyAsString(Map<String, ?> properties, String propertyName) {
        return propertyValueToString(getMandatoryProperty(properties, propertyName));
    }

    public static String getPropertyAsString(Map<String, ?> properties, String propertyName) {
        return propertyValueToString(getProperty(properties, propertyName));
    }

    public static String getPropertyAsString(Map<String, ?> properties, String propertyName, String defaultValue) {
        return propertyValueToString(getProperty(properties, propertyName, defaultValue));
    }

    public static Boolean getMandatoryPropertyAsBoolean(Map<String, ?> properties, String propertyName) {
        return propertyValueToBoolean(getMandatoryProperty(properties, propertyName));
    }

    public static Boolean getPropertyAsBoolean(Map<String, ?> properties, String propertyName) {
        return propertyValueToBoolean(getProperty(properties, propertyName));
    }

    public static Boolean getPropertyAsBoolean(Map<String, ?> properties, String propertyName, String defaultValue) {
        return propertyValueToBoolean(getProperty(properties, propertyName, defaultValue));
    }

    public static String propertyValueToString(Object propertyValue) {
        try {
            return propertyValue != null ? ((propertyValue instanceof String) ? String.valueOf(propertyValue) : JSONUtil.toString(propertyValue)) : null;
        } catch (JsonProcessingException e) {
            throw new PropertyAccessException("Cannot convert property value " + propertyValue + " to string");
        }
    }

    public static Boolean propertyValueToBoolean(Object propertyValue) {
        return propertyValue != null ? Boolean.parseBoolean(String.valueOf(propertyValue)) : null;
    }

    public static Object getProperty(Map<String, ?> properties, String path) {
        Object propertyValue = properties.get(path);
        if (propertyValue != null) {
            return propertyValue;
        }
        propertyValue = getComplexProperty(properties, path);
        if (propertyValue != null) {
            return propertyValue;
        } else {
            return null;
        }
    }

    public static Object getProperty(Map<String, ?> properties, String path, Object defaultValue) {
        Object propertyValue = getProperty(properties, path);
        if (propertyValue != null) {
            return propertyValue;
        } else {
            return defaultValue;
        }
    }

    public static Object getMandatoryProperty(Map<String, ?> properties, String path) {
        Object propertyValue = getProperty(properties, path);
        if (propertyValue != null) {
            return propertyValue;
        } else {
            throw new PropertyAccessException("Property <" + path + "> is required but missing");
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

    /**
     * Try to get a value following a path in a map or a list/array. For example :
     * MapUtil.get(map, "a.b.c") correspond to:
     * map.get(a).get(b).get(c)
     * MapUtil.get(list, "1.b.c.2") correspond to:
     * list[1].get(b).get(c)[2]
     *
     * @param object the map/list to search for path
     * @param path   keys in the map separated by '.'
     */
    static Object getComplexProperty(Object object, String path) {
        if (StringUtils.isBlank(path)) {
            throw new IllegalFunctionException("Path is empty, cannot evaluate property for object " + object);
        }
        String[] tokens = path.split("[\\.\\]\\[]");

        Object value = object;
        for (String token : tokens) {
            if (StringUtils.isEmpty(token)) {
                continue;
            }
            if (value instanceof List) {
                List<Object> nested = (List<Object>) value;
                try {
                    int index = Integer.parseInt(token);
                    value = nested.get(index);
                    if (value == null) {
                        return null;
                    }
                } catch (NumberFormatException e) {
                    return null;
                }
            } else if (value instanceof Object[]) {
                Object[] nested = (Object[]) value;
                try {
                    int index = Integer.parseInt(token);
                    value = nested[index];
                    if (value == null) {
                        return null;
                    }
                } catch (NumberFormatException e) {
                    return null;
                }
            } else if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) value;
                value = nested.get(token);
                if (value == null) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return value;
    }
}
