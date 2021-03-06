package com.toscaruntime.util;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JSONUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static Map<String, Object> toMap(String json) throws IOException {
        JavaType mapStringObjectType = OBJECT_MAPPER.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class);
        return OBJECT_MAPPER.readValue(json, mapStringObjectType);
    }

    public static List<Object> toList(String json) throws IOException {
        JavaType type = OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, Object.class);
        return OBJECT_MAPPER.readValue(json, type);
    }

    public static Object toObject(String json) throws IOException {
        JavaType type = OBJECT_MAPPER.getTypeFactory().constructType(Object.class);
        return OBJECT_MAPPER.readValue(json, type);
    }

    public static String toString(Object object) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(object);
    }

    public static <T> T toObject(Map<String, Object> raw, Class<T> clazz) throws IOException {
        return OBJECT_MAPPER.convertValue(raw, clazz);
    }
}
