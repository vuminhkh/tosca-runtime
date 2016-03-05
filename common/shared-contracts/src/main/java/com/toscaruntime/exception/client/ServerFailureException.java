package com.toscaruntime.exception.client;

import com.toscaruntime.exception.ThirdPartyException;

/**
 * Thrown when remote execution of workflows fail. This exception is not due to user but is due to the server.
 *
 * @author Minh Khang VU
 */
public class ServerFailureException extends ThirdPartyException {

    public ServerFailureException(String message) {
        super(message);
    }
}
