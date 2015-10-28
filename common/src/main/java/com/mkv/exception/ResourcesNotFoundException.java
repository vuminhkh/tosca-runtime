package com.mkv.exception;

public class ResourcesNotFoundException extends NonRecoverableException {

    public ResourcesNotFoundException(String message) {
        super(message);
    }
}
