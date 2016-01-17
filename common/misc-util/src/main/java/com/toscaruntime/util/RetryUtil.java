package com.toscaruntime.util;

import java.util.concurrent.TimeUnit;

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

    public interface Predicate {

        boolean isSatisfied();
    }

    public static boolean waitUntilPredicateIsSatisfied(Predicate predicate, long coolDown, TimeUnit timeUnit) {
        return waitUntilPredicateIsSatisfied(predicate, coolDown, -1, timeUnit);
    }

    public static boolean waitUntilPredicateIsSatisfied(Predicate predicate, long coolDown, long timeout, TimeUnit timeUnit) {
        long before = System.currentTimeMillis();
        long maxWait;
        if (timeout > 0) {
            maxWait = before + timeUnit.toMillis(timeout);
        } else {
            maxWait = Long.MAX_VALUE;
        }
        while (true) {
            if (predicate.isSatisfied()) {
                return true;
            } else {
                if (System.currentTimeMillis() > maxWait) {
                    return false;
                }
                try {
                    Thread.sleep(timeUnit.toMillis(coolDown));
                } catch (InterruptedException e) {
                    return false;
                }
            }
        }
    }

    @SafeVarargs
    public static <T> T doActionWithRetry(Action<T> action, String name, int times, long coolDownInMillis, Class<? extends Throwable>... recoverableErrors) throws Throwable {
        int currentTimes = 0;
        while (true) {
            try {
                currentTimes++;
                return action.doAction();
            } catch (Throwable t) {
                if (isRecoverableError(t.getClass(), recoverableErrors) && currentTimes < times) {
                    // Retry if it's recoverable error
                    Thread.sleep(coolDownInMillis);
                    if (t.getMessage() == null) {
                        log.warn(currentTimes + " attempt to execute " + name + ", sleep " + coolDownInMillis + " and retry ", t);
                    } else {
                        log.warn(currentTimes + " attempt to execute " + name + ", sleep " + coolDownInMillis + " and retry " + t.getMessage());
                    }
                } else {
                    throw t;
                }
            }
        }
    }
}
