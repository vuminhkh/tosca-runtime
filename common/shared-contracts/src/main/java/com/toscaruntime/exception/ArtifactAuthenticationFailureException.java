package com.toscaruntime.exception;

public class ArtifactAuthenticationFailureException extends ArtifactException {

    public ArtifactAuthenticationFailureException(String message) {
        super(message);
    }

    public ArtifactAuthenticationFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
