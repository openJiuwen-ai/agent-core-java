/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ClientConfigSupport
 *
 * @since 0.1.7
 */
final class ClientConfigSupport {
    /**
     * ClientConfigSupport.
     * 
     * @since 0.1.7
     */
    private ClientConfigSupport() {
    }

    static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    static boolean asBoolean(Object value, boolean isDefaultValue) {
        if (value == null) {
            return isDefaultValue;
        }
        if (value instanceof Boolean isValue) {
            return isValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    static int asInt(Object value, int isDefaultValue) {
        if (value == null) {
            return isDefaultValue;
        }
        if (value instanceof Number isValue) {
            return isValue.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    static Integer asNullableInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number isValue) {
            return isValue.intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    static Double asNullableDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number isValue) {
            return isValue.doubleValue();
        }
        return Double.valueOf(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, mapValue) -> {
            if (key != null) {
                result.put(String.valueOf(key), mapValue);
            }
        });
        return result;
    }

    @SuppressWarnings("unchecked")
    static Map<String, String> asStringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, String> result = new LinkedHashMap<>();
        map.forEach((key, mapValue) -> {
            if (key != null && mapValue != null) {
                result.put(String.valueOf(key), String.valueOf(mapValue));
            }
        });
        return result;
    }
}
