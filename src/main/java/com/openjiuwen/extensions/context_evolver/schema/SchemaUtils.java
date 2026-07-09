/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared helpers for the context_evolver schema DTOs.
 * 
 * @since 0.1.7
 */
public final class SchemaUtils {
    /**
     * SchemaUtils.
     * 
     * @since 0.1.7
     */
    private SchemaUtils() {
        // Utility class
    }

    /**
     * md5Hex.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    public static String md5Hex(String value) {
        String input = value != null ? value : "";
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 digest unavailable", e);
        }
    }

    /**
     * stringValue.
     * 
     * @param value value
     * @param defaultValue defaultValue
     * @return the result
     * @since 0.1.7
     */
    public static String stringValue(Object value, String defaultValue) {
        return value != null ? String.valueOf(value) : defaultValue;
    }

    /**
     * intValue.
     * 
     * @param value value
     * @param defaultValue defaultValue
     * @return the result
     * @since 0.1.7
     */
    public static int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * doubleValue.
     * 
     * @param value value
     * @param defaultValue defaultValue
     * @return the result
     * @since 0.1.7
     */
    public static double doubleValue(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Double.parseDouble(stringValue);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * booleanValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    public static Boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return null;
    }

    /**
     * instantValue.
     * 
     * @param value value
     * @param defaultValue defaultValue
     * @return the result
     * @since 0.1.7
     */
    public static Instant instantValue(Object value, Instant defaultValue) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof String stringValue) {
            try {
                return Instant.parse(stringValue);
            } catch (DateTimeParseException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * mapValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    public static Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    /**
     * stringListValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    public static List<String> stringListValue(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> rawList) {
            for (Object item : rawList) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
        }
        return result;
    }

    /**
     * toPayload.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    public static Object toPayload(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ACEMemory aceMemory) {
            return aceMemory.toMap();
        }
        if (value instanceof ACERetrievedMemory aceRetrievedMemory) {
            return aceRetrievedMemory.toMap();
        }
        if (value instanceof ReasoningBankMemory reasoningBankMemory) {
            return reasoningBankMemory.toMap();
        }
        if (value instanceof ReasoningBankMemoryItem reasoningBankMemoryItem) {
            return reasoningBankMemoryItem.toMap();
        }
        if (value instanceof ReasoningBankRetrievedMemory reasoningBankRetrievedMemory) {
            return reasoningBankRetrievedMemory.toMap();
        }
        if (value instanceof ReMeMemory reMeMemory) {
            return reMeMemory.toMap();
        }
        if (value instanceof ReMeMemoryMetadata reMeMemoryMetadata) {
            return reMeMemoryMetadata.toMap();
        }
        if (value instanceof ReMeRetrievedMemory reMeRetrievedMemory) {
            return reMeRetrievedMemory.toMap();
        }
        if (value instanceof TaskMemory taskMemory) {
            return taskMemory.toMap();
        }
        if (value instanceof PersonalMemory personalMemory) {
            return personalMemory.toMap();
        }
        if (value instanceof Trajectory trajectory) {
            return trajectory.toDict();
        }
        if (value instanceof List<?> listValue) {
            List<Object> converted = new ArrayList<>(listValue.size());
            for (Object item : listValue) {
                converted.add(toPayload(item));
            }
            return converted;
        }
        if (value instanceof Map<?, ?>) {
            Map<String, Object> converted = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : mapValue(value).entrySet()) {
                converted.put(entry.getKey(), toPayload(entry.getValue()));
            }
            return converted;
        }
        return value;
    }

    /**
     * toPayloadMap.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    public static Map<String, Object> toPayloadMap(Object value) {
        Object payload = toPayload(value);
        return payload instanceof Map<?, ?> ? mapValue(payload) : new LinkedHashMap<>();
    }
}
