package com.toscaruntime.exception.deployment.creation;

import com.toscaruntime.exception.UnexpectedException;

/**
 * Multiple provider hook is not an expected behaviour
 *
 * @author Minh Khang VU
 */
public class MultipleProviderHooksFoundException extends UnexpectedException {

    public MultipleProviderHooksFoundException(String message) {
        super(message);
    }
}
