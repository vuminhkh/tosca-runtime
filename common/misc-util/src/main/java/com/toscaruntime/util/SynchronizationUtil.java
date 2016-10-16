package com.toscaruntime.util;

import com.toscaruntime.exception.InterruptedByUserException;

import java.util.concurrent.TimeUnit;

public class SynchronizationUtil {

    public interface Predicate {

        boolean isSatisfied();
    }

    public static boolean waitUntilPredicateIsSatisfied(Predicate predicate, int times, long coolDown, TimeUnit timeUnit) {
        int currentTimes = 0;
        while (true) {
            if (predicate.isSatisfied()) {
                return true;
            } else {
                if (currentTimes++ > times) {
                    return false;
                }
                try {
                    Thread.sleep(timeUnit.toMillis(coolDown));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedByUserException("Retry interrupted at " + currentTimes, e);
                }
            }
        }
    }
}
