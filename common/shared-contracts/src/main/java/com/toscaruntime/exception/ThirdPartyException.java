package com.toscaruntime.exception;

/**
 * Third party exception, can be file system, openstack, docker, persistence.
 * When that happens, normally there are nothing more to do other than log the error and inform the user.
 * We should try at best to assure data / state consistency when this kind of error happens.
 *
 * @author Minh Khang VU
 */
public abstract class ThirdPartyException extends RuntimeException {
    public ThirdPartyException(String message) {
        super(message);
    }

    public ThirdPartyException(String message, Throwable cause) {
        super(message, cause);
    }
}
