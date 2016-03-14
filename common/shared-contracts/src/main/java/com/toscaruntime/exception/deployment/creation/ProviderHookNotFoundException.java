package com.toscaruntime.exception.deployment.creation;

import com.toscaruntime.exception.UnexpectedException;

/**
 * No provider hook is found cannot initialize the deployment
 *
 * @author Minh Khang VU
 */
public class ProviderHookNotFoundException extends UnexpectedException {

    public ProviderHookNotFoundException(String message) {
        super(message);
    }
}
