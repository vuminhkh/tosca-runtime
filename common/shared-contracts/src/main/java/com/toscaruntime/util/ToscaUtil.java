package com.toscaruntime.util;

import com.toscaruntime.tosca.ToscaTime;

public class ToscaUtil {

    public static long convertToSeconds(String timeInText) {
        return new ToscaTime(timeInText).convertToUnit("s").value().get().longValue();
    }
}
