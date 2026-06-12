/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;

/**
 * Mirrors Python's {@code JsonUtils} in
 * {@code openjiuwen/core/common/security/json_utils.py}.
 */
public final class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final LoggerProtocol LOGGER = Loggers.COMMON;

    private JsonUtils() {
    }

    public static Object safeJsonLoads(String jsonString) {
        return safeJsonLoads(jsonString, null);
    }

    public static Object safeJsonLoads(String jsonString, Object defaultValue) {
        if (defaultValue == null) {
            try {
                return readJson(jsonString);
            } catch (IllegalArgumentException exception) {
                throw ErrorHelper.buildError(
                        StatusCode.COMMON_JSON_INPUT_PROCESS_ERROR,
                        "error_msg",
                        "JSON type error"
                );
            } catch (JsonProcessingException exception) {
                throw ErrorHelper.buildError(
                        StatusCode.COMMON_JSON_INPUT_PROCESS_ERROR,
                        "error_msg",
                        "JSON decode error"
                );
            } catch (Exception exception) {
                throw ErrorHelper.buildError(
                        StatusCode.COMMON_JSON_INPUT_PROCESS_ERROR,
                        "error_msg",
                        "JSON operation error"
                );
            }
        }

        try {
            return readJson(jsonString);
        } catch (IllegalArgumentException exception) {
            LOGGER.error("JSON type error: {}", exception.getMessage());
            return defaultValue;
        } catch (JsonProcessingException exception) {
            LOGGER.error("JSON decode error: {}", exception.getOriginalMessage());
            return defaultValue;
        } catch (Exception exception) {
            LOGGER.error("JSON operation error: {}", exception.getMessage());
            return defaultValue;
        }
    }

    public static String safeJsonDumps(Object obj) {
        Object result = safeJsonDumps(obj, null);
        return result == null ? null : String.valueOf(result);
    }

    public static Object safeJsonDumps(Object obj, Object defaultValue) {
        if (defaultValue == null) {
            try {
                return OBJECT_MAPPER.writeValueAsString(obj);
            } catch (InvalidDefinitionException exception) {
                throw ErrorHelper.buildError(
                        StatusCode.COMMON_JSON_EXECUTION_PROCESS_ERROR,
                        "error_msg",
                        "JSON serialization type error"
                );
            } catch (JsonMappingException exception) {
                throw ErrorHelper.buildError(
                        StatusCode.COMMON_JSON_EXECUTION_PROCESS_ERROR,
                        "error_msg",
                        "JSON serialization value error"
                );
            } catch (JsonProcessingException exception) {
                throw ErrorHelper.buildError(
                        StatusCode.COMMON_JSON_EXECUTION_PROCESS_ERROR,
                        "error_msg",
                        "JSON serialization error"
                );
            } catch (Exception exception) {
                throw ErrorHelper.buildError(
                        StatusCode.COMMON_JSON_EXECUTION_PROCESS_ERROR,
                        "error_msg",
                        "JSON serialization error"
                );
            }
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (InvalidDefinitionException exception) {
            LOGGER.error("JSON serialization type error");
            return defaultValue;
        } catch (JsonMappingException exception) {
            LOGGER.error("JSON serialization value error");
            return defaultValue;
        } catch (JsonProcessingException exception) {
            LOGGER.error("JSON serialization error");
            return defaultValue;
        } catch (Exception exception) {
            LOGGER.error("JSON serialization error");
            return defaultValue;
        }
    }

    private static Object readJson(String jsonString) throws JsonProcessingException {
        if (jsonString == null) {
            throw new IllegalArgumentException("json_string is null");
        }
        return OBJECT_MAPPER.readValue(jsonString, Object.class);
    }
}
