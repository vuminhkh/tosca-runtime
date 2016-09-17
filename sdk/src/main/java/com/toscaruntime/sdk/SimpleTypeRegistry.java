package com.toscaruntime.sdk;

import com.toscaruntime.exception.deployment.configuration.ToscaTypeNotRegisteredException;
import tosca.nodes.Root;

import java.util.Map;

public class SimpleTypeRegistry implements TypeRegistry {

    private Map<String, Class<?>> classMap;

    public SimpleTypeRegistry(Map<String, Class<?>> classMap) {
        this.classMap = classMap;
    }

    @Override
    public Class<? extends Root> findInstanceType(String typeName) {
        Class<? extends Root> loaded = (Class<? extends Root>) this.classMap.get(typeName);
        if (loaded == null) {
            try {
                return (Class<? extends Root>) Class.forName(typeName, true, Root.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new ToscaTypeNotRegisteredException(typeName + " cannot be found");
            }
        }
        return loaded;
    }

    @Override
    public Class<? extends tosca.relationships.Root> findRelationshipInstanceType(String typeName) {
        Class<? extends tosca.relationships.Root> loaded = (Class<? extends tosca.relationships.Root>) this.classMap.get(typeName);
        if (loaded == null) {
            try {
                return (Class<? extends tosca.relationships.Root>) Class.forName(typeName, true, tosca.relationships.Root.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new ToscaTypeNotRegisteredException(typeName + " cannot be found");
            }
        }
        return loaded;
    }
}
