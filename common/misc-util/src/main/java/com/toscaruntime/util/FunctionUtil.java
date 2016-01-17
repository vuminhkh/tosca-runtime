package com.toscaruntime.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utilities to evaluate functions
 *
 * @author Minh Khang VU
 */
public class FunctionUtil {

    public static String concat(Object... memberValue) {
        StringBuilder buffer = new StringBuilder();
        for (Object member : memberValue) {
            buffer.append(PropertyUtil.propertyValueToString(member));
        }
        return buffer.toString();
    }

    public static String[] setEntityToSelf(String[] paths) {
        List<String> newPaths = new ArrayList<>(Arrays.asList(paths));
        newPaths.set(0, "SELF");
        return newPaths.toArray(new String[newPaths.size()]);
    }
}
