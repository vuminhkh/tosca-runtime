package com.toscaruntime.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class ComparatorUtil {

    public static int hashCode(Object object) {
        if (object instanceof Map) {
            return hashCodeMap((Map<?, ?>) object);
        } else if (object instanceof Collection) {
            return hashCodeStream(((Collection) object).stream());
        } else if (object instanceof Object[]) {
            return hashCodeStream(Arrays.stream((Object[]) object));
        } else {
            return object == null ? 0 : object.hashCode();
        }
    }

    private static int hashCodeMap(Map<?, ?> map) {
        return map.entrySet().stream().mapToInt(entry -> (entry.getKey() == null ? 0 : entry.getKey().hashCode()) ^
                (entry.getValue() == null ? 0 : hashCode(entry.getValue()))).sum();
    }

    private static int hashCodeStream(Stream<?> stream) {
        return stream.mapToInt(ComparatorUtil::hashCode).sum();
    }

    public static boolean equals(Object left, Object right) {
        if (left instanceof Map) {
            return right instanceof Map && equalsMap((Map<?, ?>) left, (Map<?, ?>) right);
        } else if (left instanceof Object[]) {
            return right instanceof Object[] && equalsStream(Arrays.stream((Object[]) left), Arrays.stream((Object[]) right));
        } else if (left instanceof Collection<?>) {
            return right instanceof Collection<?> && equalsStream(((Collection) left).stream(), ((Collection) right).stream());
        } else {
            return Objects.equals(left, right);
        }
    }

    private static boolean equalsMap(Map<?, ?> left, Map<?, ?> right) {
        if (left == right) {
            return true;
        }
        if (left == null) {
            return false;
        }
        if (left.size() != right.size()) {
            return false;
        }
        try {
            for (Map.Entry<?, ?> e : left.entrySet()) {
                Object key = e.getKey();
                Object value = e.getValue();
                if (value == null) {
                    if (!(right.get(key) == null && right.containsKey(key)))
                        return false;
                } else {
                    if (!equals(value, right.get(key)))
                        return false;
                }
            }
        } catch (ClassCastException unused) {
            return false;
        } catch (NullPointerException unused) {
            return false;
        }
        return true;
    }

    private static boolean equalsStream(Stream<?> left, Stream<?> right) {
        Iterator<?> leftIter = left.iterator(), rightIter = right.iterator();
        while (leftIter.hasNext() && rightIter.hasNext()) {
            boolean elementCompare = equals(leftIter.next(), rightIter.next());
            if (!elementCompare) {
                return false;
            }
        }
        return true;
    }
}
