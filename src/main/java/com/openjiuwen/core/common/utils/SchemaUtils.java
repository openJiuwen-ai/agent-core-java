// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.utils;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ValidationError;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Schema工具类
 *
 * <p>处理JSON Schema和Java类的转换、数据格式化和验证。</p>
 *
 * <p>注意：由于Java是静态类型语言，部分Python动态特性无法完全实现。</p>
 */
public final class SchemaUtils {

    /**
     * 私有构造函数，防止实例化
     */
    private SchemaUtils() {
    }

    /**
     * 根据提供的schema格式化数据，填充默认值。
     *
     * @param data 要格式化的数据
     * @param schema JSON Schema字典或Java类
     * @param skipNoneValue 是否跳过null值
     * @param skipValidate 是否跳过验证
     * @return 格式化后的数据，填充了默认值
     * @throws ValidationError 如果数据无法按照schema格式化
     */
    public static Object formatWithSchema(Object data, Object schema,
                                         boolean skipNoneValue, boolean skipValidate) {
        Object newData = skipNoneValue ? removeNoneValues(data) : data;

        // 首先验证数据
        if (!skipValidate) {
            validateWithSchema(newData, schema);
        }

        // 格式化数据（简化实现）
        return newData;
    }

    /**
     * 递归地从数据结构中移除null值。
     *
     * @param data 输入数据
     * @return 清理后的数据结构，移除了null值
     */
    @SuppressWarnings("unchecked")
    public static Object removeNoneValues(Object data) {
        if (data == null) {
            return null;
        }

        if (data instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) data;
            Map<String, Object> cleanedMap = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                Object value = entry.getValue();
                Object cleanedValue = removeNoneValues(value);
                if (cleanedValue != null) {
                    cleanedMap.put(key.toString(), cleanedValue);
                }
            }
            return cleanedMap.isEmpty() ? null : cleanedMap;
        } else if (data instanceof List<?>) {
            List<?> list = (List<?>) data;
            List<Object> cleanedList = new ArrayList<>();
            for (Object item : list) {
                Object cleanedItem = removeNoneValues(item);
                if (cleanedItem != null) {
                    cleanedList.add(cleanedItem);
                }
            }
            return cleanedList.isEmpty() ? null : cleanedList;
        } else if (data instanceof String || data instanceof Number || data instanceof Boolean) {
            return data;
        } else {
            try {
                return data.toString();
            } catch (Exception e) {
                return null;
            }
        }
    }

    /**
     * 使用提供的schema验证数据。
     *
     * @param data 要验证的数据
     * @param schema JSON Schema字典或Java类
     * @throws ValidationError 如果数据验证失败
     */
    @SuppressWarnings("unchecked")
    public static void validateWithSchema(Object data, Object schema) {
        // 简化实现：仅进行基本类型检查
        if (schema instanceof Map) {
            validateAgainstJsonSchema(data, (Map<String, Object>) schema);
        } else if (schema instanceof Class<?>) {
            // Java类验证（简化实现）
            // 实际实现可以使用Bean Validation
            Class<?> clazz = (Class<?>) schema;
            if (clazz != null) {
                // 简单的类型检查
                if (data != null && !clazz.isInstance(data)) {
                    throw new IllegalArgumentException(
                        "Data type mismatch: expected " + clazz.getName() + ", got " + data.getClass().getName()
                    );
                }
            }
        }
    }

    /**
     * 从Java类获取JSON Schema字典。
     *
     * @param schemaClass Java类
     * @return JSON Schema字典表示
     */
    public static Map<String, Object> getSchemaDict(Class<?> schemaClass) {
        if (schemaClass == null) {
            return null;
        }

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("title", schemaClass.getSimpleName());

        // 简化实现：返回基本schema
        // 实际实现可以使用Jackson或其他库从Java类生成JSON Schema
        return schema;
    }

    /**
     * 从JSON Schema字典获取Java类。
     *
     * @param schemaDict JSON Schema字典
     * @return 从schema生成的Java类（简化实现返回Map.class）
     */
    public static Class<?> getSchemaClass(Map<String, Object> schemaDict) {
        if (schemaDict == null) {
            return null;
        }
        // 简化实现：Java无法动态创建类，返回Map.class
        return Map.class;
    }

    /**
     * 验证数据是否符合JSON Schema（简化实现）
     *
     * @param data 数据
     * @param schema JSON Schema
     */
    private static void validateAgainstJsonSchema(Object data, Map<String, Object> schema) {
        // 简化实现：仅验证基本类型
        String expectedType = (String) schema.get("type");

        if (expectedType == null) {
            return; // 无类型限制
        }

        if (data == null) {
            return; // null可以匹配任何类型（简化实现）
        }

        boolean isValid = switch (expectedType) {
            case "string" -> data instanceof String;
            case "integer" -> data instanceof Integer || data instanceof Long;
            case "number" -> data instanceof Number;
            case "boolean" -> data instanceof Boolean;
            case "array" -> data instanceof List;
            case "object" -> data instanceof Map;
            case "null" -> data == null;
            default -> true; // 其他类型不做检查
        };

        if (!isValid) {
            throw new IllegalArgumentException(
                "Type mismatch: expected " + expectedType + ", got " + data.getClass().getSimpleName()
            );
        }

        // 验证嵌套结构
        if ("object".equals(expectedType) && data instanceof Map) {
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            if (properties != null) {
                Map<String, Object> dataMap = (Map<String, Object>) data;
                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                    String propName = entry.getKey();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> propSchema = (Map<String, Object>) entry.getValue();
                    Object propValue = dataMap.get(propName);
                    validateAgainstJsonSchema(propValue, propSchema);
                }
            }
        } else if ("array".equals(expectedType) && data instanceof List) {
            Map<String, Object> itemsSchema = (Map<String, Object>) schema.get("items");
            if (itemsSchema != null) {
                List<?> dataList = (List<?>) data;
                for (Object item : dataList) {
                    validateAgainstJsonSchema(item, itemsSchema);
                }
            }
        }
    }

    /**
     * 深度合并两个Map
     *
     * @param base 基础Map
     * @param override 覆盖Map
     * @return 合并后的Map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> deepMerge(Map<String, Object> base,
                                                Map<String, Object> override) {
        Map<String, Object> result = new HashMap<>(base);

        for (Map.Entry<String, Object> entry : override.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (result.containsKey(key) && result.get(key) instanceof Map &&
                value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> merged = deepMerge(
                    (Map<String, Object>) result.get(key),
                    (Map<String, Object>) value
                );
                result.put(key, merged);
            } else {
                result.put(key, value);
            }
        }

        return result;
    }

    /**
     * 获取嵌套值
     *
     * @param map 数据Map
     * @param path 点分隔的路径（如"user.name"）
     * @return 值，如果不存在返回null
     */
    @SuppressWarnings("unchecked")
    public static Object getNestedValue(Map<String, Object> map, String path) {
        if (map == null || path == null || path.isEmpty()) {
            return null;
        }

        String[] parts = path.split("\\.");
        Object current = map;

        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return null;
            }
        }

        return current;
    }

    /**
     * 设置嵌套值
     *
     * @param map 数据Map
     * @param path 点分隔的路径（如"user.name"）
     * @param value 要设置的值
     */
    @SuppressWarnings("unchecked")
    public static void setNestedValue(Map<String, Object> map, String path, Object value) {
        if (map == null || path == null || path.isEmpty()) {
            return;
        }

        String[] parts = path.split("\\.");
        Map<String, Object> current = map;

        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            Object next = current.get(part);

            if (next == null || !(next instanceof Map)) {
                next = new HashMap<String, Object>();
                current.put(part, next);
            }

            current = (Map<String, Object>) next;
        }

        current.put(parts[parts.length - 1], value);
    }
}