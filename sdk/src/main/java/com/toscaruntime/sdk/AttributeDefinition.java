package com.toscaruntime.sdk;

/**
 * All attribute definition that needs to be evaluated at runtime must implement this interface
 *
 * @author Minh Khang VU
 */
public interface AttributeDefinition {

    String evaluateAttribute();
}
