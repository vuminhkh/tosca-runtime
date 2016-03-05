package com.toscaruntime.exception.deployment.artifact;

import com.toscaruntime.exception.ThirdPartyException;

public class ArtifactAuthenticationFailureException extends ThirdPartyException {

    public ArtifactAuthenticationFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
