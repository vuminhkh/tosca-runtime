package com.toscaruntime.exception;

public class ResourcesNotFoundException extends NonRecoverableException {

    public ResourcesNotFoundException(String message) {
        super(message);
    }
}
