package com.toscaruntime.exception.deployment.execution;

import com.toscaruntime.exception.ThirdPartyException;

/**
 * Happens when provider resource cannot be allocated, it's usually due to the IAAS (excess of quota etc ...)
 *
 * @author Minh Khang VU
 */
public class ProviderResourceAllocationException extends ThirdPartyException {

    public ProviderResourceAllocationException(String message) {
        super(message);
    }

    public ProviderResourceAllocationException(String message, Throwable cause) {
        super(message, cause);
    }
}
