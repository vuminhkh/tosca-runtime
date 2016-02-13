package com.toscaruntime.exception;

public class ArtifactUploadException extends ArtifactException {

    public ArtifactUploadException(String message) {
        super(message);
    }

    public ArtifactUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
