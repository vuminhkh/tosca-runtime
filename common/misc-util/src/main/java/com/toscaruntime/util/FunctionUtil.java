package com.toscaruntime.util;

import java.util.List;

import com.google.common.collect.Lists;

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

    public static String[] setEntityToSelf(String[] paths) {
        List<String> newPaths = Lists.newArrayList(paths);
        newPaths.set(0, "SELF");
        return newPaths.toArray(new String[newPaths.size()]);
    }
}
