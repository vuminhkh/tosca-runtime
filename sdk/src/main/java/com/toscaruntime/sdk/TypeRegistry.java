package com.toscaruntime.sdk;

/**
 * Factory to create tosca node / relationship instance
 */
public interface TypeRegistry {

    Class<? extends tosca.nodes.Root> findInstanceType(String typeName);

    Class<? extends tosca.relationships.Root> findRelationshipInstanceType(String typeName);
}
