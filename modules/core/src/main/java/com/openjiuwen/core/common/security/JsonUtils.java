/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Safe JSON serialization/deserialization utilities.
 * <p>
 * Uses Jackson for JSON processing.
 * 
 * @since 0.1.7
 */
public final class JsonUtils {
    private static final Logger LOG = LoggerFactory.getLogger(JsonUtils.class);

    /**
     * ObjectMapper.
     * 
     * @since 0.1.7
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * JsonUtils.
     * 
     * @since 0.1.7
     */
    private JsonUtils() {
    }

    /**
     * Safely parse a JSON string. If {@code defaultValue} is null, errors are thrown;
     * otherwise errors are logged and the default is returned.
     * 
     * @param json json
     * @param type type
     * @param defaultValue defaultValue
     * @return the result
     * @since 0.1.7
     */
    public static <T> T safeJsonLoads(String json, Class<T> type, T defaultValue) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            if (defaultValue == null) {
                throw ErrorHelper.buildError(StatusCode.COMMON_JSON_INPUT_PROCESS_ERROR, "JSON decode error", null, e,
                        null);
            }
            LOG.error("JSON decode error: {}", e.getMessage());
            return defaultValue;
        } catch (Exception e) {
            if (defaultValue == null) {
                throw ErrorHelper.buildError(StatusCode.COMMON_JSON_INPUT_PROCESS_ERROR, "JSON operation error", null,
                        e, null);
            }
            LOG.error("JSON operation error: {}", e.getMessage());
            return defaultValue;
        }
    }

    /**
     * Parse JSON string — throws on error.
     * 
     * @param json json
     * @param type type
     * @return the result
     * @since 0.1.7
     */
    public static <T> T safeJsonLoads(String json, Class<T> type) {
        return safeJsonLoads(json, type, null);
    }

    /**
     * Safely serialize an object to JSON. If {@code defaultValue} is null, errors are thrown;
     * otherwise errors are logged and the default is returned.
     * 
     * @param obj obj
     * @param defaultValue defaultValue
     * @return the result
     * @since 0.1.7
     */
    public static String safeJsonDumps(Object obj, String defaultValue) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            if (defaultValue == null) {
                throw ErrorHelper.buildError(StatusCode.COMMON_JSON_EXECUTION_PROCESS_ERROR, "JSON serialization error",
                        null, e, null);
            }
            LOG.error("JSON serialization error: {}", e.getMessage());
            return defaultValue;
        } catch (Exception e) {
            if (defaultValue == null) {
                throw ErrorHelper.buildError(StatusCode.COMMON_JSON_EXECUTION_PROCESS_ERROR, "JSON serialization error",
                        null, e, null);
            }
            LOG.error("JSON serialization error: {}", e.getMessage());
            return defaultValue;
        }
    }

    /**
     * Serialize to JSON — throws on error.
     * 
     * @param obj obj
     * @return the result
     * @since 0.1.7
     */
    public static String safeJsonDumps(Object obj) {
        return safeJsonDumps(obj, null);
    }

    /**
     * Get the shared ObjectMapper instance for advanced usage.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static ObjectMapper getMapper() {
        return MAPPER;
    }
}
