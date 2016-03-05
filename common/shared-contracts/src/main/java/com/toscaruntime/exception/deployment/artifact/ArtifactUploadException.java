package com.toscaruntime.exception.deployment.artifact;

import com.toscaruntime.exception.ThirdPartyException;

public class ArtifactUploadException extends ThirdPartyException {

    public ArtifactUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
