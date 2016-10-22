package com.toscaruntime.util;

import com.toscaruntime.exception.InterruptedByUserException;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExceptionUtil {

    private static final Logger log = LoggerFactory.getLogger(ExceptionUtil.class);

    /**
     * Check whether the given exception has a nested {@link InterruptedException}, if true then throw {@link com.toscaruntime.exception.InterruptedByUserException}
     *
     * @param e the exception to check
     * @throws com.toscaruntime.exception.InterruptedByUserException notify that user has interrupted execution
     */
    public static void checkInterrupted(Exception e) {
        boolean isInterrupted = ExceptionUtils.indexOfType(e, InterruptedException.class) >= 0;
        if (isInterrupted) {
            log.info("Execution has been interrupted by user");
            throw new InterruptedByUserException("Execution has been interrupted", e);
        }
    }
}
