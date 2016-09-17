package com.toscaruntime.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.toscaruntime.exception.deployment.configuration.IllegalFunctionException;
import com.toscaruntime.exception.deployment.configuration.PropertyAccessException;
import com.toscaruntime.exception.deployment.configuration.PropertyRequiredException;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class PropertyUtil {

    public static Map<String, String> flatten(Map<String, Object> properties) {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, Object> propertyEntry : properties.entrySet()) {
            Object flatten = doFlatten(propertyEntry.getKey(), propertyEntry.getValue());
            if (flatten instanceof String) {
                result.put(propertyEntry.getKey(), (String) flatten);
            } else if (flatten instanceof Map) {
                result.putAll((Map<String, String>) flatten);
            }
        }
        return result;
    }

    private static Object doFlatten(String prefix, Object property) {
        if (property instanceof Map) {
            return doFlattenMap(prefix, (Map<String, Object>) property);
        } else if (property instanceof Object[]) {
            return doFlattenList(prefix, Arrays.asList((Object[]) property));
        } else if (property instanceof Collection) {
            return doFlattenList(prefix, (Collection<Object>) property);
        } else {
            return property != null ? String.valueOf(property) : null;
        }
    }

    private static Map<String, String> doFlattenMap(String prefix, Map<String, Object> mapProperty) {
        Map<String, String> flattenProperties = new HashMap<>();
        for (Map.Entry<String, Object> propertyEntry : mapProperty.entrySet()) {
            String entryPrefix = prefix + "." + propertyEntry.getKey();
            Object flattenValue = doFlatten(entryPrefix, propertyEntry.getValue());
            if (flattenValue instanceof String) {
                flattenProperties.put(entryPrefix, (String) flattenValue);
            } else if (flattenValue instanceof Map) {
                flattenProperties.putAll((Map<String, String>) flattenValue);
            }
        }
        return flattenProperties;
    }

    private static Map<String, String> doFlattenList(String prefix, Collection<Object> listProperty) {
        Map<String, String> flattenProperties = new HashMap<>();
        int i = 0;
        for (Object property : listProperty) {
            String entryPrefix = prefix + "[" + i + "]";
            Object flattenValue = doFlatten(entryPrefix, property);
            if (flattenValue instanceof String) {
                flattenProperties.put(entryPrefix, (String) flattenValue);
            } else if (flattenValue instanceof Map) {
                flattenProperties.putAll((Map<String, String>) flattenValue);
            }
            i++;
        }
        return flattenProperties;
    }

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

    public static String propertyValueToString(Object propertyValue) {
        try {
            return propertyValue != null ? ((propertyValue instanceof String) ? String.valueOf(propertyValue) : JSONUtil.toString(propertyValue)) : null;
        } catch (JsonProcessingException e) {
            throw new PropertyAccessException("Cannot convert property value " + propertyValue + " to string");
        }
    }

    public static Object getProperty(Map<String, ?> properties, String path) {
        if (properties == null) {
            return null;
        }
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
            throw new PropertyRequiredException("Property <" + path + "> is required but missing");
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
