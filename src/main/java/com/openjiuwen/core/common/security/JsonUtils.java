// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JSON 安全解析和序列化工具类
 *
 * <p>提供安全的 JSON 解析和序列化方法，支持默认值和错误处理。</p>
 */
public final class JsonUtils {

    private static final Logger LOGGER = Logger.getLogger(JsonUtils.class.getName());
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonUtils() {
        // 防止实例化
    }

    /**
     * 安全解析 JSON 字符串
     *
     * <p>当解析失败时抛出 BaseError 异常。</p>
     *
     * @param jsonString JSON 字符串
     * @return 解析后的对象
     * @throws BaseError 当 JSON 解析失败时
     */
    public static Object safeJsonLoads(String jsonString) throws BaseError {
        try {
            if (jsonString == null) {
                throw BaseError.builder()
                        .status(StatusCode.COMMON_JSON_INPUT_PROCESS_ERROR)
                        .details("JSON input is null")
                        .build();
            }
            return OBJECT_MAPPER.readValue(jsonString, Object.class);
        } catch (JsonProcessingException e) {
            throw BaseError.builder()
                    .status(StatusCode.COMMON_JSON_INPUT_PROCESS_ERROR)
                    .details("JSON decode error: " + e.getOriginalMessage())
                    .cause(e)
                    .build();
        }
    }

    /**
     * 安全解析 JSON 字符串，支持默认值
     *
     * <p>当解析失败时返回指定的默认值，并记录错误日志。</p>
     *
     * @param jsonString JSON 字符串
     * @param defaultValue 解析失败时的默认值
     * @return 解析后的对象，或默认值
     */
    public static Object safeJsonLoads(String jsonString, Object defaultValue) {
        try {
            return OBJECT_MAPPER.readValue(jsonString, Object.class);
        } catch (JsonProcessingException e) {
            LOGGER.log(Level.SEVERE, "JSON decode error: {0}", e.getOriginalMessage());
            return defaultValue;
        }
    }

    /**
     * 安全序列化对象为 JSON 字符串
     *
     * <p>当序列化失败时抛出 BaseError 异常。</p>
     *
     * @param obj 要序列化的对象
     * @return JSON 字符串
     * @throws BaseError 当 JSON 序列化失败时
     */
    public static String safeJsonDumps(Object obj) throws BaseError {
        try {
            if (obj == null) {
                throw BaseError.builder()
                        .status(StatusCode.COMMON_JSON_EXECUTION_PROCESS_ERROR)
                        .details("JSON input is null")
                        .build();
            }
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw BaseError.builder()
                    .status(StatusCode.COMMON_JSON_EXECUTION_PROCESS_ERROR)
                    .details("JSON serialization error: " + e.getOriginalMessage())
                    .cause(e)
                    .build();
        }
    }

    /**
     * 安全序列化对象为 JSON 字符串，支持默认值
     *
     * <p>当序列化失败时返回指定的默认值，并记录错误日志。</p>
     *
     * @param obj 要序列化的对象
     * @param defaultValue 序列化失败时的默认值
     * @return JSON 字符串，或默认值
     */
    public static String safeJsonDumps(Object obj, String defaultValue) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            LOGGER.log(Level.SEVERE, "JSON serialization error: {0}", e.getOriginalMessage());
            return defaultValue;
        }
    }
}