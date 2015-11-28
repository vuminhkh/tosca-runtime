package com.toscaruntime.util;

/**
 * Utilities to evaluate functions
 *
 * @author Minh Khang VU
 */
public class FunctionUtil {

    public static String concat(Object... memberValue) {
        StringBuilder buffer = new StringBuilder();
        for (Object member : memberValue) {
            buffer.append(member);
        }
        return buffer.toString();
    }
}
