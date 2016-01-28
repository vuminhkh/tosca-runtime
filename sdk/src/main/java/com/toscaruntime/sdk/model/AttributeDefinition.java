package com.toscaruntime.sdk.model;

/**
 * All attribute definition that needs to be evaluated at runtime must implement this interface
 *
 * @author Minh Khang VU
 */
public interface AttributeDefinition {

    Object evaluateAttribute();
}
