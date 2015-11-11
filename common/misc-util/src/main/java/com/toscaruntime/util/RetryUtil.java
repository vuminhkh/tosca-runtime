package com.toscaruntime.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An utility to retry to do actions
 *
 * @author Minh Khang VU
 */
public class RetryUtil {

    private static final Logger log = LoggerFactory.getLogger(RetryUtil.class);

    public interface Action<T> {

        String getName();

        T doAction() throws Throwable;
    }

    @SafeVarargs
    private static boolean isRecoverableError(Class<? extends Throwable> error, Class<? extends Throwable>... recoverableErrors) {
        for (Class<? extends Throwable> recoverableError : recoverableErrors) {
            if (recoverableError.isAssignableFrom(error)) {
                return true;
            }
        }
        return false;
    }

    @SafeVarargs
    public static <T> T doActionWithRetry(Action<T> action, int times, long coolDownInMillis, Class<? extends Throwable>... recoverableErrors) throws Throwable {
        int currentTimes = 0;
        while (true) {
            try {
                currentTimes++;
                return action.doAction();
            } catch (Throwable t) {
                if (isRecoverableError(t.getClass(), recoverableErrors) && currentTimes < times) {
                    // Retry if it's recoverable error
                    Thread.sleep(coolDownInMillis);
                    log.warn(currentTimes + " attempt to execute " + action.getName() + ", sleep " + coolDownInMillis + " and retry " + t.getMessage());
                } else {
                    throw t;
                }
            }
        }
    }
}
