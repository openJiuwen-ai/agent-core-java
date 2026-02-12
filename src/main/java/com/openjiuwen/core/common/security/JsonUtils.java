package com.openjiuwen.core.common.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LogManager;

/**
 * JSON安全工具类
 * 
 * <p>提供安全的JSON序列化和反序列化功能，带异常处理和容错机制。
 * 
 * @author OpenJiuwen
 * @since 2026-01-29
 */
public class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonUtils() {
        // Utility class
    }

    /**
     * 安全的JSON解析
     * 
     * <p>如果defaultValue为null，解析失败时抛出异常；
     * 如果defaultValue不为null，解析失败时返回defaultValue并记录日志。
     * 
     * @param <T> 目标类型
     * @param jsonString JSON字符串
     * @param defaultValue 默认值
     * @param clazz 目标类型的Class对象
     * @return 解析后的对象
     * @throws com.openjiuwen.core.common.exception.JiuWenBaseException 当defaultValue为null且解析失败时
     */
    public static <T> T safeJsonLoads(String jsonString, T defaultValue, Class<T> clazz) {
        if (defaultValue == null) {
            try {
                return OBJECT_MAPPER.readValue(jsonString, clazz);
            } catch (JsonProcessingException e) {
                ExceptionUtils.raiseException(
                    StatusCode.COMMON_JSON_INPUT_PROCESS_ERROR,
                    "JSON decode error: " + e.getMessage(),
                    e
                );
                return null; // unreachable
            }
        } else {
            try {
                return OBJECT_MAPPER.readValue(jsonString, clazz);
            } catch (JsonProcessingException e) {
                LogManager.getLogger("JsonUtils").error("JSON decode error: " + e.getMessage());
                return defaultValue;
            } catch (Exception e) {
                LogManager.getLogger("JsonUtils").error("JSON operation error: " + e.getMessage());
                return defaultValue;
            }
        }
    }

    /**
     * 安全的JSON序列化
     * 
     * <p>如果defaultValue为null，序列化失败时抛出异常；
     * 如果defaultValue不为null，序列化失败时返回defaultValue并记录日志。
     * 
     * @param obj 要序列化的对象
     * @param defaultValue 默认值
     * @return JSON字符串
     * @throws com.openjiuwen.core.common.exception.JiuWenBaseException 当defaultValue为null且序列化失败时
     */
    public static String safeJsonDumps(Object obj, String defaultValue) {
        if (defaultValue == null) {
            try {
                return OBJECT_MAPPER.writeValueAsString(obj);
            } catch (JsonProcessingException e) {
                ExceptionUtils.raiseException(
                    StatusCode.COMMON_JSON_EXECUTION_PROCESS_ERROR,
                    "JSON serialization error: " + e.getMessage(),
                    e
                );
                return null; // unreachable
            }
        } else {
            try {
                return OBJECT_MAPPER.writeValueAsString(obj);
            } catch (JsonProcessingException e) {
                LogManager.getLogger("JsonUtils").error("JSON serialization error: " + e.getMessage());
                return defaultValue;
            } catch (Exception e) {
                LogManager.getLogger("JsonUtils").error("JSON operation error: " + e.getMessage());
                return defaultValue;
            }
        }
    }
}

