/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.monitor;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

final class MonitorModelSupport {

    private MonitorModelSupport() {
    }

    static Object firstPresent(Object source, String... keys) {
        if (source == null) {
            return null;
        }
        if (source instanceof Map<?, ?> map) {
            for (String key : keys) {
                if (map.containsKey(key)) {
                    return map.get(key);
                }
            }
            return null;
        }
        for (String key : keys) {
            Object value = invokeAccessor(source, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    static long longValue(Object value, long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    static Long longObjectValue(Object value) {
        if (value == null) {
            return null;
        }
        return longValue(value, 0L);
    }

    static Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    static Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? null : Boolean.parseBoolean(String.valueOf(value));
    }

    static Map<String, Object> copyMap(Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (map != null) {
            map.forEach((key, value) -> copy.put(String.valueOf(key), value));
        }
        return copy;
    }

    private static Object invokeAccessor(Object source, String key) {
        for (String methodName : new String[]{getterName(key), booleanGetterName(key), key}) {
            try {
                Method method = source.getClass().getMethod(methodName);
                return method.invoke(source);
            } catch (ReflectiveOperationException ignored) {
                // Try next accessor variant.
            }
        }
        return null;
    }

    private static String getterName(String key) {
        return "get" + capitalize(toCamel(key));
    }

    private static String booleanGetterName(String key) {
        return "is" + capitalize(toCamel(key));
    }

    private static String toCamel(String key) {
        String[] parts = key.split("_");
        if (parts.length == 0) {
            return key;
        }
        StringBuilder builder = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            builder.append(capitalize(parts[i]));
        }
        return builder.toString();
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
