package com.toscaruntime.util;

import java.util.Map;
import java.util.stream.Stream;

public class StreamUtil {

    /**
     * Safe entry stream without key or value null for a map
     *
     * @param map map
     * @param <T> key type
     * @param <U> value type
     * @return safe stream
     */
    public static <T, U> Stream<Map.Entry<T, U>> safeEntryStream(Map<T, U> map) {
        if (map == null) {
            return Stream.empty();
        } else {
            return map.entrySet().stream().filter(entry -> entry.getKey() != null && entry.getValue() != null);
        }
    }
}
