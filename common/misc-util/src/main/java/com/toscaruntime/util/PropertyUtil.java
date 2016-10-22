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
import java.util.stream.Collectors;

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

    public static Map<String, Object> convertJsonPropertiesToMapProperties(Map<String, String> flatten) {
        return StreamUtil.safeEntryStream(flatten).collect(Collectors.toMap(Map.Entry::getKey, entry -> toObject(entry.getValue())));
    }

    public static Map<String, String> convertMapPropertiesToJsonProperties(Map<String, Object> properties) {
        return StreamUtil.safeEntryStream(properties).collect(Collectors.toMap(Map.Entry::getKey, entry -> toJson(entry.getValue())));
    }

    public static String toJson(Object propertyValue) {
        try {
            return JSONUtil.toString(propertyValue);
        } catch (JsonProcessingException e) {
            throw new PropertyAccessException("Could not print <" + propertyValue + "> as json");
        }
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

    public static Object toObject(String propertyValue) {
        try {
            return JSONUtil.toObject(propertyValue);
        } catch (IOException e) {
            throw new PropertyAccessException("Could not parse <" + propertyValue + "> as a json object");
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

    public static <T> Map<String, T> getPropertyAsMap(Map<String, ?> properties, String propertyName) {
        Object propertyValue = getProperty(properties, propertyName);
        if (propertyValue instanceof Map) {
            return (Map<String, T>) propertyValue;
        } else {
            return null;
        }
    }

    public static <T> List<T> getPropertyAsList(Map<String, ?> properties, String propertyName) {
        Object propertyValue = getProperty(properties, propertyName);
        if (propertyValue instanceof List) {
            return (List<T>) propertyValue;
        } else {
            return null;
        }
    }

    public static Object propertyValueFromString(String propertyValue) {
        try {
            if (propertyValue == null) {
                return null;
            }
            String trimmedPropertyValue = propertyValue.trim();
            if (trimmedPropertyValue.startsWith("{")) {
                return JSONUtil.toMap(trimmedPropertyValue);
            } else if (trimmedPropertyValue.startsWith("[")) {
                return JSONUtil.toList(trimmedPropertyValue);
            } else {
                return propertyValue;
            }
        } catch (IOException e) {
            return propertyValue;
        }
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

    public static String getPluginConfigurationTarget(String pluginName, Map<String, Object> nodeProperties) {
        return PropertyUtil.getPropertyAsString(nodeProperties, "plugins." + pluginName + ".target", "default");
    }

    public static Map<String, Object> getPluginNodeConfiguration(String pluginName, Map<String, Object> nodeProperties) {
        return (Map<String, Object>) PropertyUtil.getProperty(nodeProperties, "plugins." + pluginName + ".configuration");
    }

    public static Map<String, Object> getPluginConfiguration(String pluginName, Map<String, Map<String, Object>> pluginProperties, Map<String, Object> overrideProperties) {
        Map<String, Object> nodeProperties = new HashMap<>();
        // A node can override the default plugin configuration target
        String target = getPluginConfigurationTarget(pluginName, overrideProperties);
        if (pluginProperties != null && pluginProperties.containsKey(target)) {
            // Get properties from plugin first so that properties from node can override
            nodeProperties.putAll(pluginProperties.get(target));
        }
        Map<String, Object> scriptPluginConfigurationFromNode = getPluginNodeConfiguration(pluginName, overrideProperties);
        if (scriptPluginConfigurationFromNode != null) {
            // Get properties from node for the plugin
            nodeProperties.putAll(scriptPluginConfigurationFromNode);
        }
        return nodeProperties;
    }
}
