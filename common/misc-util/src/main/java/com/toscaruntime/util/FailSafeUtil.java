package com.toscaruntime.util;

import com.toscaruntime.exception.InterruptedByUserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * An utility to retry to do actions
 *
 * @author Minh Khang VU
 */
public class FailSafeUtil {

    private static final Logger log = LoggerFactory.getLogger(FailSafeUtil.class);

    public interface Action<T> {
        T doAction() throws Throwable;
    }

    public interface VoidAction {
        void doAction() throws Throwable;
    }

    public interface NoExceptionAction<T> {
        T doAction();
    }

    public interface VoidNoExceptionAction {
        void doAction();
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
    public static <T> T doActionWithRetry(Action<T> action, String name, int times, long coolDownPeriod, TimeUnit timeUnit, Class<? extends Throwable>... recoverableErrors) throws Throwable {
        int currentTimes = 0;
        while (true) {
            try {
                currentTimes++;
                return action.doAction();
            } catch (Throwable t) {
                if (isRecoverableError(t.getClass(), recoverableErrors) && currentTimes < times) {
                    if (t.getMessage() == null) {
                        log.warn(currentTimes + " attempt to execute " + name + ", sleep " + coolDownPeriod + " " + timeUnit + " and retry ", t);
                    } else {
                        log.warn(currentTimes + " attempt to execute " + name + ", sleep " + coolDownPeriod + " " + timeUnit + " and retry " + t.getMessage());
                    }
                    // Retry if it's recoverable error
                    Thread.sleep(timeUnit.toMillis(coolDownPeriod));
                } else {
                    throw t;
                }
            }
        }
    }

    @SafeVarargs
    public static void doActionWithRetry(VoidAction action, String name, int times, long coolDownPeriod, TimeUnit timeUnit, Class<? extends Throwable>... recoverableErrors) throws Throwable {
        doActionWithRetry(() -> {
            action.doAction();
            return null;
        }, name, times, coolDownPeriod, timeUnit, recoverableErrors);
    }

    @SafeVarargs
    public static <T> T doActionWithRetryNoCheckedException(NoExceptionAction<T> action, String name, int times, long coolDownPeriod, TimeUnit timeUnit, Class<? extends Throwable>... recoverableErrors) {
        int currentTimes = 0;
        while (true) {
            try {
                currentTimes++;
                return action.doAction();
            } catch (RuntimeException e) {
                if (isRecoverableError(e.getClass(), recoverableErrors) && currentTimes < times) {
                    if (e.getMessage() == null) {
                        log.warn(currentTimes + " attempt to execute " + name + ", sleep " + coolDownPeriod + " " + timeUnit + " and retry ", e);
                    } else {
                        log.warn(currentTimes + " attempt to execute " + name + ", sleep " + coolDownPeriod + " " + timeUnit + " and retry " + e.getMessage());
                    }
                    try {
                        Thread.sleep(timeUnit.toMillis(coolDownPeriod));
                    } catch (InterruptedException ie) {
                        log.warn("Retry interrupted at " + currentTimes, e);
                        Thread.currentThread().interrupt();
                        throw new InterruptedByUserException("Retry interrupted at " + currentTimes, ie);
                    }
                } else {
                    throw e;
                }
            }
        }
    }

    @SafeVarargs
    public static void doActionWithRetryNoCheckedException(VoidNoExceptionAction action, String name, int times, long coolDownPeriod, TimeUnit timeUnit, Class<? extends Throwable>... recoverableErrors) {
        doActionWithRetryNoCheckedException(() -> {
            action.doAction();
            return null;
        }, name, times, coolDownPeriod, timeUnit, recoverableErrors);
    }
}
